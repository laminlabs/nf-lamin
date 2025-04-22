package nextflow.lamin

import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import nextflow.Session
import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
import nextflow.script.WorkflowMetadata
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord

import nextflow.lamin.api.LaminInstance
import nextflow.lamin.api.LaminHub

import ai.lamin.lamin_api_client.ApiException
import ai.lamin.lamin_api_client.model.GetRecordRequestBody

/**
 * Example workflow events observer
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
@Slf4j
@CompileStatic
class LaminObserver implements TraceObserver {

    protected Session session
    protected LaminConfig config
    protected LaminHub hub
    protected LaminInstance instance
    protected Map<String, String> transform
    protected Map<String, String> run

    // provenance helper values
    protected List<PathMatcher> matchers = []
    protected Set<TaskRun> tasks = []
    protected Set<Path> workflowInputs = []
    protected Map<Path,Path> workflowOutputs = [:]

    protected Lock lock = new ReentrantLock()

    @Override
    void onFlowCreate(Session session) {
        log.debug 'onFlowCreate triggered!'

        // store the session for later use
        this.session = session
        this.config = LaminConfig.createFromSession(session)

        // fetch instance settings
        this.hub = new LaminHub(config.apiKey)

        // create instance
        this.instance = new LaminInstance(
            this.hub,
            this.config.getInstanceOwner(),
            this.config.getInstanceName()
        )

        // test connection
        testConnection()

        // fetch or create Transform object
        this.transform = fetchOrCreateTransform()

        // create Run object
        this.run = createRun()
    }

    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace) {
        // log.debug "onProcessComplete triggered!"

        // keeping track of processes to be able to find out what connects to what at the end of the run.
        // might not need this level of detail at the end.
        final task = handler.task
        lock.withLock {
            tasks << task
        }

        // if need be, create artifacts from inputs
        task.getInputFilesMap().each { name, path ->
            createInputArtifact(path)
        }
    }

    @Override
    void onProcessCached(TaskHandler handler, TraceRecord trace) {
        // log.debug "onProcessCached triggered!"

        final task = handler.task
        lock.withLock {
            tasks << task
        }
        task.getInputFilesMap().each { name, path ->
            createInputArtifact(path)
        }
    }

    // TODO: implement tracking an output artifact
    @Override
    void onFilePublish(Path destination, Path source) {
        // log.debug "onFilePublish triggered!"
        final match = matchers.isEmpty() || matchers.any { matcher -> matcher.matches(destination) }
        if (!match) {
            return
        }

        lock.withLock {
            workflowOutputs[source] = destination
        }

    // log.debug "onFilePublish triggered!"
    // log.debug "Create Artifact object:\n" +
    //     "  artifact = ln.Artifact(\n" +
    //     "    run=run,\n" +
    //     "    data=\"${destination.toUriString()}\",\n" +
    //     "  )\n"
    }

    @Override
    void onFlowError(TaskHandler handler, TraceRecord trace) {
        log.debug 'onFlowError triggered!'
        finalizeRun()
    }

    @Override
    void onFlowComplete() {
        log.debug 'onFlowComplete triggered!'
        finalizeRun()
    }

    // --- private methods ---
    protected void testConnection() {
        if (!this.instance) {
            throw new IllegalStateException('API client is null')
        }

        String instanceString = "${this.instance.getOwner()}/${this.instance.getName()}"
        try {
            this.instance.getInstanceStatistics()
            log.info "Connected to Lamin instance: ${instanceString}"
        } catch (ApiException e) {
            log.error "Could not connect to Lamin instance: ${instanceString}!"
            log.error 'API call failed: ' + e.getMessage()
        }
    }

    protected Map fetchOrCreateTransform() {
        if (!this.session) {
            throw new IllegalStateException('Session is null')
        }

        // collect information about the workflow run
        WorkflowMetadata wfMetadata = this.session.getWorkflowMetadata()

        log.trace "wfMetadata [${wfMetadata}]"
        log.trace "manifest: ${wfMetadata.manifest.toMap()}"

        String description = "${wfMetadata.manifest.getName()}: ${wfMetadata.manifest.getDescription()}"

        // store repository name, commit id, and key
        String repository = wfMetadata.repository ?: wfMetadata.projectName
        String commitId = wfMetadata.commitId
        String mainScript = wfMetadata.scriptFile.toString().replaceFirst("${wfMetadata.projectDir}/", '')
        String revision = wfMetadata.revision

        // create data for Transform object
        String key = mainScript == 'main.nf' ? repository : "${repository}:${mainScript}"
        Map info = [
            'repository': repository,
            'main-script': mainScript,
            'commit-id': commitId,
            'revision': revision
        ]
        String infoAsJson = groovy.json.JsonOutput.toJson(info)

        log.debug 'Fetch or create Transform object:\n' +
            '  transform = ln.Transform(\n' +
            "    key=\"${key}\",\n" +
            "    version=\"${revision}\",\n" +
            "    source_code='''${infoAsJson}''',\n" +
            '    type=\"pipeline\",\n' +
            "    reference=\"${wfMetadata.repository}\",\n" +
            '    reference_type=\"url\",\n' +
            "    description=\"${description}\"\n" +
            ').save()\n'

        return [id: 1, uid: 'abcdef123456']
    }

    protected Map createRun() {
        if (!this.session) {
            throw new IllegalStateException('Session is null')
        }
        if (!this.transform) {
            throw new IllegalStateException('Transform is null')
        }

        // collect information about the workflow run
        WorkflowMetadata wfMetadata = this.session.getWorkflowMetadata()

        log.debug 'Create Run object:\n' +
            "  transform = ln.Transform.get(\"${this.transform.uid}\")\n" +
            '  run = ln.Run(\n' +
            '    transform=transform,\n' +
            "    name=\"${wfMetadata.runName}\",\n" +
            "    created_at=\"${wfMetadata.start}\",\n" +
            "    started_at=\"${wfMetadata.start}\",\n" +
            '    reference=\"https://cloud.seqera.io/...\",\n' +
            '    reference_type=\"url\",\n' +
            '    project=...\n' +
            '    created_by=...\n' +
            ').save()\n'

        return [id: 1, uid: 'abcdef123456']
    }

    protected void finalizeRun() {
        if (!this.session) throw new IllegalStateException('Session is null')
        if (!this.transform) throw new IllegalStateException('Transform is null')

        WorkflowMetadata wfMetadata = this.session.getWorkflowMetadata()
        log.debug 'Finalise Run object:\n' +
            "  run = ln.Run.get(\"${this.run.uid}\")\n" +
            "  run.finished_at = \"${wfMetadata.complete}\"\n"
        '  run.environment = ...\n' +
            '  run.report = ...\n' +
            '  run.save()\n'
    }

    // TODO: implement tracking an input artifact
    protected void createInputArtifact(Path path) {
        // if path is already in workflowInputs, do nothing
        if (workflowInputs.contains(path)) {
            return
        }

        // log.debug "Create Artifact object:\n" +
        //     "  artifact = ln.Artifact(\n" +
        //     "    run=run,\n" +
        //     "    data=\"${path.toUriString()}\",\n" +
        //     "  )\n"
        lock.withLock {
            workflowInputs << path
        }
    }

}
