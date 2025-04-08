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
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord

import nextflow.lamin.api.LaminApiClient
import nextflow.lamin.api.LaminHubClient

import ai.lamin.lamin_api_client.ApiException;
import ai.lamin.lamin_api_client.model.GetRecordRequestBody;

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
    protected LaminHubClient hubClient
    protected LaminApiClient apiClient

    protected List<PathMatcher> matchers = []

    protected Set<TaskRun> tasks = []

    protected Set<Path> workflowInputs = []
    protected Map<Path,Path> workflowOutputs = [:]

    protected Lock lock = new ReentrantLock()

    @Override
    void onFlowCreate(Session session) {
        // store the session for later use
        this.session = session
        this.config = LaminConfig.createFromSession(session)

        // fetch instance settings
        this.hubClient = new LaminHubClient(config.apiKey)

        // create apiClient
        this.apiClient = new LaminApiClient(
            this.hubClient,
            this.config.getInstanceOwner(),
            this.config.getInstanceName()
        )


        log.info "nf-lamin> onFlowCreate triggered!"

        def wfMetadata = session.getWorkflowMetadata()
        
        // session.baseDir + "/" + session.scriptName

        String description = session.config.navigate("manifest.description") as String

        // store repository name, commit id, and key
        String repoName = wfMetadata.repository ?: wfMetadata.projectName
        String commitId = wfMetadata.commitId
        String mainScript = wfMetadata.scriptFile.toString().replaceFirst("${wfMetadata.projectDir}/", "")
        
        String key = mainScript == "main.nf" ? repoName : "${repoName}:${mainScript}"
        String sourceCode = "${repoName}@${commitId}:${mainScript}"

        log.info "nf-lamin> Fetch or create Transform object:\n" +
            "  trafo = ln.Transform(\n" +
            "    key=\"${key}\",\n" +
            "    version=\"${wfMetadata.revision}\",\n" +
            "    source_code=\"${sourceCode}\",\n" +
            "    type=\"pipeline\",\n" +
            "    reference=\"${wfMetadata.repository}\",\n" +
            "    reference_type=\"url\",\n" +
            "    description=\"${wfMetadata.manifest.getDescription()}\"\n" +
            "  )\n"

        log.info "nf-lamin> Create Run object:\n" +
            "  run = ln.Run(\n" +
            "    transform=trafo,\n" +
            "    name=\"${wfMetadata.runName}\",\n" +
            "    started_at=\"${wfMetadata.start}\"\n" +
            "  )\n"

        // trying to fetch a record from the server
        try {
            Integer limitToMany = 10;
            Boolean includeForeignKeys = true;
            GetRecordRequestBody getRecordRequestBody = new GetRecordRequestBody();
            
            Object result = this.apiClient.getRecord(
                // moduleName: "core",
                // modelName: "artifact",
                // idOrUid: "MDG7BbeFVPvEyyUb0000",
                // includeForeignKeys: true
                "core",
                "artifact",
                "MDG7BbeFVPvEyyUb0000",
                limitToMany,
                includeForeignKeys,
                getRecordRequestBody
            );
            log.info "nf-lamin> Fetched data from server: ${result.toString()}"
        } catch (ApiException e) {
            log.error "nf-lamin> Exception when calling LaminApiClient#getRecord"
            log.error "API call failed: " + e.getMessage()
            log.error "Status code: " + e.getCode()
            log.error "Response body: " + e.getResponseBody()
            log.error "Response headers: " + e.getResponseHeaders()
        }
    }

    void printWorkflowMetadata(nextflow.script.WorkflowMetadata wfMetadata) {
        log.info "nf-lamin> Printing wfMetadata"
        for (key in ["scriptId", "scriptFile", "scriptName", "repository", "commitId", "revision", "projectDir", "projectName", "start", "container", "commandLine", "nextflow", "outputDir", "workDir", "launchDir", "profile", "sessionId", "resume", "stubRun", "preview", "runName", "containerEngine", "configFiles", "stats", "userName", "homeDir", "manifest", "wave", "fusion", "failOnIgnore"]) {
            log.info "nf-lamin>   wfMetadata.$key: ${wfMetadata[key]}"
        }
    }

    void printSession(nextflow.Session session) {
        log.info "nf-lamin>   session.binding: ${session.binding}"
        def configKeys = session.config.collect{k, v -> k}
        for (key in configKeys) {
            log.info "nf-lamin>   session.config.$key: ${session.config[key]}"
        }
        for (key in ["cacheable", "resumeMode", "outputDir", "workDir", "bucketDir", "baseDir", "scriptName", "script", "runName", "stubRun", "preview", "profile", "commandLine", "commitId"]) {
            log.info "nf-lamin>   session.$key: ${session[key]}"
        }
    }


    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace) {
        // skip failed tasks
        final task = handler.task
        if( !task.isSuccess() )
            return

        log.info "nf-lamin> onProcessComplete name='${task.name}' triggered! InputMap:"
        lock.withLock {
            tasks << task
            task.getInputFilesMap().each { name, path ->
                log.info "* name=$name, path=$path"
                onFileInput(path)
            }
        }
    }

    void onFileInput(Path path) {
        // if path is already in workflowInputs, do nothing
        if (workflowInputs.contains(path)) {
            return
        }

        log.info "nf-lamin> Create Artifact object:\n" +
            "  artifact = ln.Artifact(\n" +
            "    run=run,\n" +
            "    data=\"${path.toUriString()}\",\n" +
            "  )\n"
        lock.withLock {
            workflowInputs << path
        }
    }

    @Override
    void onProcessCached(TaskHandler handler, TraceRecord trace) {
        final task = handler.task
        lock.withLock {
            tasks << task
            task.getInputFilesMap().each { name, path ->
                onFileInput(path)
            }
        }
    }


    @Override
    void onFilePublish(Path destination, Path source) {
        final match = matchers.isEmpty() || matchers.any { matcher -> matcher.matches(destination) }
        if( !match )
            return

        lock.withLock {
            workflowOutputs[source] = destination
        }

        log.info "nf-lamin> onFilePublish triggered!"
        log.info "nf-lamin> Create Artifact object:\n" +
            "  artifact = ln.Artifact(\n" +
            "    run=run,\n" +
            "    data=\"${destination.toUriString()}\",\n" +
            "  )\n"
    }
    
    @Override
    void onFlowError(TaskHandler handler, TraceRecord trace) {
        //log.info "nf-lamin> onFlowError name='${handler.task.name}'"
    }

    @Override
    void onFlowComplete() {
        log.info "nf-lamin> onFlowComplete triggered!"
    }
}
