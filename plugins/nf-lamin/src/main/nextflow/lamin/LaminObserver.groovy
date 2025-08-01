package nextflow.lamin

import java.net.URI
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
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

import nextflow.lamin.instance.Instance
import nextflow.lamin.hub.LaminHub

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
    protected Instance instance
    protected Map transform
    protected Map run

    protected Lock lock = new ReentrantLock()

    @Override
    void onFlowCreate(Session session) {
        // store the session and config for later use
        LaminPlugin.setSession(session)
        this.session = session
        this.config = LaminPlugin.getConfig()

        // fetch instance settings
        this.hub = new LaminHub(
            this.config.supabaseApiUrl,
            this.config.supabaseAnonKey,
            this.config.apiKey
        )

        // create instance
        this.instance = new Instance(
            this.hub,
            this.config.instanceOwner,
            this.config.instanceName,
            this.config.getMaxRetries(),
            this.config.getRetryDelay()
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
    // handler.task.getInputFilesMap().each { name, path ->
    //     createInputArtifact(path)
    // }
    }

    @Override
    void onProcessCached(TaskHandler handler, TraceRecord trace) {
    // handler.task.getInputFilesMap().each { name, path ->
    //     createInputArtifact(path)
    // }
    }

    @Override
    void onFilePublish(Path destination, Path source) {
        createOutputArtifact(this.run, source, destination)
    }

    @Override
    void onFlowError(TaskHandler handler, TraceRecord trace) {
        finalizeRun()
    }

    @Override
    void onFlowComplete() {
        finalizeRun()
    }

    // --- private methods ---
    protected void testConnection() {
        String instanceString = "${this.instance.getOwner()}/${this.instance.getName()}"
        try {
            Map account = this.instance.getAccount()
            log.info "✅ Connected to LaminDB instance '${instanceString}' as '${account.handle}'"
        } catch (ApiException e) {
            log.error "❌ Could not connect to LaminDB instance '${instanceString}'!"
            log.error 'API call failed: ' + e.getMessage()
        }
    }

    protected Map fetchOrCreateTransform() {
        // collect information about the workflow run
        WorkflowMetadata wfMetadata = this.session.getWorkflowMetadata()

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
            transform = this.instance.createTransform(
                key: key,
                source_code: infoAsJson,
                version: revision,
                type: 'pipeline',
                reference: wfMetadata.repository,
                reference_type: 'url',
                description: description
            )
        }

        log.info "Transform ${transform.uid} (${this.config.getWebUrl()}/${this.instance.getOwner()}/${this.instance.getName()}/transform/${transform.uid})"
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

        log.info "Run ${run.uid} (${this.config.getWebUrl()}/${this.instance.getOwner()}/${this.instance.getName()}/transform/${this.transform.uid}/${run.uid})"

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

    protected Map fetchOrCreateStorage(Path path) {
        String root = path.getFileSystem().toString()
        URI uri = path.toUri()
        String type = uri.getScheme()

        // Search for existing Storage object
        List<Map> existingStorage = this.instance.getRecords(
            moduleName: 'core',
            modelName: 'storage',
            filter: [
                and: [
                    [root: [eq: root]],
                    [type: [eq: type]]
                ]
            ]
        )
        log.debug "Found ${existingStorage.size()} existing Storage(s) with root ${root} and type ${type}"

        Map storage = null
        if (existingStorage) {
            if (existingStorage.size() > 1) {
                log.warn "Found multiple Storage objects with root ${root} and type ${type}"
            }
            storage = existingStorage[0]
        } else {
            // create Storage object
            storage = this.instance.createRecord(
                moduleName: 'core',
                modelName: 'storage',
                data: [
                    root: root,
                    type: type
                ]
            )
        }
        return storage
    }

    // TODO: implement tracking an input artifact
    protected Map createOutputArtifact(Map run, Path localPath, Path destPath) {
        Boolean isLocalFile = destPath.toUri().getScheme() == 'file'

        // Arguments
        String runUid = run.uid.toString()
        Integer runId = run.id as Integer
        String description = "Output artifact for run ${runId}".toString()

        log.debug "Creating output artifact for run ${runId} at ${destPath.toUri()}"

        Map artifact = null
        lock.lock()
        try {
            if (isLocalFile) {
                File file = destPath.toFile()
                artifact = this.instance.uploadArtifact(
                    file: file,
                    run_id: runId,
                    description: description
                )
            } else {
                String path = destPath.toUri().toString();
                artifact = this.instance.createArtifact(
                    path: path,
                    run_id: runId,
                    description: description
                )
            }

        } catch (Exception e) {
            log.error "Failed to create output artifact for run ${runId} at ${destPath.toUri()}"
            log.debug "Exception: ${e.getMessage()}", e
            return null
        } finally {
            lock.unlock()
        }

        String verb = artifact.run != runId ? 'Detected previous' : isLocalFile ? 'Uploaded' : 'Created'
        log.debug "$verb output artifact ${artifact.uid} (${this.config.getWebUrl()}/${this.instance.getOwner()}/${this.instance.getName()}/artifact/${artifact.uid})"

        return artifact
    }

}
