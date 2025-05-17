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

import ai.lamin.lamin_api_client.ApiException

import nextflow.lamin.api.LaminInstance
import nextflow.lamin.hub.LaminHub
import nextflow.lamin.api.arguments.*

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
    protected Map transform
    protected Map run

    protected Lock lock = new ReentrantLock()

    @Override
    void onFlowCreate(Session session) {
        log.debug 'onFlowCreate triggered!'

        // store the session for later use
        this.session = session
        if (!this.session) {
            throw new IllegalStateException('Session is null')
        }

        this.config = LaminConfig.createFromSession(session)
        if (!this.config) {
            throw new IllegalStateException('LaminConfig is null')
        }

        // fetch instance settings
        this.hub = new LaminHub(config.apiKey)

        // create instance
        this.instance = new LaminInstance(
            this.hub,
            this.config.getInstanceOwner(),
            this.config.getInstanceName()
        )
        if (!this.instance) {
            throw new IllegalStateException('Lamin instance is null')
        }

        // test connection
        testConnection()

        // fetch or create Transform object
        this.transform = fetchOrCreateTransform()
        if (!this.transform) {
            throw new IllegalStateException('Transform object is null')
        }

        // create Run object
        this.run = createRun()
        if (!this.run) {
            throw new IllegalStateException('Run object is null')
        }
    }

    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace) {
        log.debug 'onProcessComplete triggered!'
    // handler.task.getInputFilesMap().each { name, path ->
    //     createInputArtifact(path)
    // }
    }

    @Override
    void onProcessCached(TaskHandler handler, TraceRecord trace) {
        log.debug 'onProcessCached triggered!'
    // handler.task.getInputFilesMap().each { name, path ->
    //     createInputArtifact(path)
    // }
    }

    // TODO: implement tracking an output artifact
    @Override
    void onFilePublish(Path destination, Path source) {
        log.debug 'onFilePublish triggered!'
    // log.debug 'Create Artifact object:\n' +
    //     '  artifact = ln.Artifact(\n' +
    //     '    run=run,\n' +
    //     '    data=\'${destination.toUriString()}\',\n' +
    //     '  )\n'
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
        // collect information about the workflow run
        WorkflowMetadata wfMetadata = this.session.getWorkflowMetadata()
        // log.trace "wfMetadata [${wfMetadata}]"
        // log.trace "manifest: ${wfMetadata.manifest.toMap()}"

        // collect info about the workflow
        String repository = wfMetadata.repository ?: wfMetadata.projectName
        String mainScript = wfMetadata.scriptFile.toString().replaceFirst("${wfMetadata.projectDir}/", '')
        String revision = wfMetadata.revision
        String key = mainScript == 'main.nf' ? repository : "${repository}:${mainScript}"

        // Search for existing Transform object
        log.debug "Searching for existing Transform with key ${key} and revision ${revision}"
        List<Map> existingTransforms = this.instance.getRecords(
            moduleName: 'core',
            modelName: 'transform',
            filter: [
                and: [
                    [key: [eq: key]],
                    // NOTE: if the user didn't provide a revision,
                    // should we use 'latest' or the commit id?
                    [version: [eq: revision]]
                ]
            ]
        )
        log.debug "Found ${existingTransforms.size()} existing Transform(s) with key ${key} and revision ${revision}"

        Map transform = null

        if (existingTransforms) {
            if (existingTransforms.size() > 1) {
                log.warn "Found multiple Transform objects with key ${key} and revision ${revision}"
            }
            transform = existingTransforms[0]
        } else {
            // collect info for new Transform object
            String description = "${wfMetadata.manifest.getName()}: ${wfMetadata.manifest.getDescription()}"
            String commitId = wfMetadata.commitId
            Map info = [
                'repository': repository,
                'main-script': mainScript,
                'commit-id': commitId,
                'revision': revision
            ]
            String infoAsJson = groovy.json.JsonOutput.toJson(info)

            // create Transform object
            transform = this.instance.createRecord(
                moduleName: 'core',
                modelName: 'transform',
                data: [
                    key: key,
                    source_code: infoAsJson,
                    version: revision,
                    type: 'pipeline',
                    reference: wfMetadata.repository,
                    reference_type: 'url',
                    description: description,
                    is_latest: true
                ]
            )
        }

        log.info "Using transform ${transform.uid} (https://lamin.ai/${this.instance.getOwner()}/${this.instance.getName()}/transform/${transform.uid})"
        return transform
    // todo: link to project?
    }

    protected Map createRun() {
        WorkflowMetadata wfMetadata = this.session.getWorkflowMetadata()

        Map run = this.instance.createRecord(
            moduleName: 'core',
            modelName: 'run',
            data: [
                transform_id: this.transform.id,
                name: wfMetadata.runName,
                created_at: wfMetadata.start,
                started_at: wfMetadata.start,
                _status_code: -1
            ]
        )

        log.info "Started run ${run.uid} (https://lamin.ai/${this.instance.getOwner()}/${this.instance.getName()}/transform/${this.transform.uid}/${run.uid})"

        return run
    // todo: link to project?
    }

    protected void finalizeRun() {
        WorkflowMetadata wfMetadata = this.session.getWorkflowMetadata()

        this.instance.updateRecord(
            moduleName: 'core',
            modelName: 'run',
            uid: this.run.uid,
            data: [
                finished_at: wfMetadata.complete,
                _status_code: wfMetadata.exitStatus
            ]
        )
    }

    // TODO: implement tracking an input artifact
    protected void createInputArtifact(Path path) {
        // if path is already in workflowInputs, do nothing
        // if (workflowInputs.contains(path)) {
        //     return
        // }

    // log.debug "Create Artifact object:\n" +
    //     "  artifact = ln.Artifact(\n" +
    //     "    run=run,\n" +
    //     "    data=\"${path.toUriString()}\",\n" +
    //     "  )\n"
    }

}
