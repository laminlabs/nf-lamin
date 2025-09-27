/*
 * Copyright 2025, Lamin Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.lamin.nf_lamin

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
import ai.lamin.nf_lamin.instance.Instance
import ai.lamin.nf_lamin.instance.InstanceSettings
import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.hub.LaminHubConfigResolver
import ai.lamin.nf_lamin.model.RunStatus

/**
 * Implements workflow events observer for Lamin provenance tracking
 *
 * This observer tracks workflow execution events and creates corresponding
 * records in Lamin for provenance and metadata management.
 */
@Slf4j
@CompileStatic
class LaminObserver implements TraceObserver {

    private Session session
    private LaminConfig config
    private Map<String, Object> resolvedConfig
    private LaminHub hub
    private Instance instance
    private Map transform
    private Map run
    private Lock lock = new ReentrantLock()

    @Override
    void onFlowCreate(Session session) {
        log.debug "LaminObserver.onFlowCreate"
        this.session = session

        // Parse configuration
        this.config = LaminConfig.parseConfig(session)
        log.debug "Parsed config: ${config.toString()}"

        // Resolve hub-specific configuration
        this.resolvedConfig = LaminHubConfigResolver.resolve(config)
        log.debug "Resolved config with hub settings"

        // Create hub client
        this.hub = new LaminHub(
            resolvedConfig.supabaseApiUrl as String,
            resolvedConfig.supabaseAnonKey as String,
            config.apiKey
        )
        log.debug "Created LaminHub client"

        // Get instance settings
        InstanceSettings settings = hub.getInstanceSettings(config.instanceOwner, config.instanceName)
        log.debug "Instance settings: ${settings.toString()}"

        // Create instance
        this.instance = new Instance(
            this.hub,
            settings,
            config.maxRetries,
            config.retryDelay
        )

        // Test connection
        testConnection()

        // Fetch or create Transform object
        this.transform = fetchOrCreateTransform()

        // Create Run object with "scheduled" status
        this.run = createRun()
    }

    @Override
    void onFlowBegin() {
        log.debug "LaminObserver.onFlowBegin"
        // Update run status from "scheduled" (-3) to "started" (-1) when workflow execution begins
        if (this.run) {
            WorkflowMetadata wfMetadata = this.session.getWorkflowMetadata()
            this.instance.updateRecord(
                moduleName: 'core',
                modelName: 'run',
                uid: this.run.uid,
                data: [
                    started_at: wfMetadata.start,
                    _status_code: RunStatus.STARTED.code
                ]
            )
            log.info "Run ${this.run.uid} ${RunStatus.STARTED.description}"
        }
    }

    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace) {
        log.debug "LaminObserver.onProcessComplete: ${handler.task.name}"
        // TODO: Implement input artifact tracking
        // handler.task.getInputFilesMap().each { name, path ->
        //     createInputArtifact(path)
        // }
    }

    @Override
    void onProcessCached(TaskHandler handler, TraceRecord trace) {
        log.debug "LaminObserver.onProcessCached: ${handler.task.name}"
        // TODO: Implement input artifact tracking
        // handler.task.getInputFilesMap().each { name, path ->
        //     createInputArtifact(path)
        // }
    }

    @Override
    void onFilePublish(Path destination, Path source) {
        log.debug "LaminObserver.onFilePublish: ${source} -> ${destination}"
        createOutputArtifact(this.run, source, destination)
    }

    @Override
    void onFlowComplete() {
        log.debug "LaminObserver.onFlowComplete"
        finalizeRun(RunStatus.COMPLETED)
    }

    @Override
    void onFlowError(TaskHandler handler, TraceRecord trace) {
        log.debug "LaminObserver.onFlowError"
        finalizeRun(RunStatus.ERRORED)
    }

    protected void testConnection() {
        String instanceString = "${this.instance.getOwner()}/${this.instance.getName()}"
        try {
            Map account = this.instance.getAccount()
            log.info "→ connected lamindb: '${instanceString}' as '${account.handle}'"
        } catch (ApiException e) {
            log.error "✗ Could not connect lamindb: '${instanceString}'!"
            log.error 'API call failed: ' + e.getMessage()
        }
    }

    protected Map fetchOrCreateTransform() {
        // Collect information about the workflow run
        WorkflowMetadata wfMetadata = this.session.getWorkflowMetadata()

        // Collect info about the workflow
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
                    // NOTE: if the user didn't provide a revision, should we use 'latest' or the commit id?
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
            // Collect info for new Transform object
            String description = "${wfMetadata.manifest.getName()}: ${wfMetadata.manifest.getDescription()}"
            String commitId = wfMetadata.commitId
            Map info = [
                'repository': repository,
                'main-script': mainScript,
                'commit-id': commitId,
                'revision': revision
            ]
            String infoAsJson = groovy.json.JsonOutput.toJson(info)

            // Create Transform object
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

        String webUrl = resolvedConfig.webUrl as String
        log.info "Transform ${transform.uid} (${webUrl}/${this.instance.getOwner()}/${this.instance.getName()}/transform/${transform.uid})"
        return transform
        // TODO: link to project?
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
                _status_code: RunStatus.SCHEDULED.code
            ]
        )

        String webUrl = resolvedConfig.webUrl as String
        log.info "Run ${run.uid} (${webUrl}/${this.instance.getOwner()}/${this.instance.getName()}/transform/${this.transform.uid}/${run.uid})"
        return run
        // TODO: link to project?
    }

    protected void finalizeRun(RunStatus status) {
        log.info "Run ${this.run.uid} ${status.description}"
        WorkflowMetadata wfMetadata = this.session.getWorkflowMetadata()
        this.instance.updateRecord(
            moduleName: 'core',
            modelName: 'run',
            uid: this.run.uid,
            data: [
                finished_at: wfMetadata.complete,
                _status_code: status.code
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
            // Create Storage object
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
                String path = destPath.toUri().toString()
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
        String webUrl = resolvedConfig.webUrl as String
        log.debug "$verb output artifact ${artifact.uid} (${webUrl}/${this.instance.getOwner()}/${this.instance.getName()}/artifact/${artifact.uid})"
        return artifact
    }
}
