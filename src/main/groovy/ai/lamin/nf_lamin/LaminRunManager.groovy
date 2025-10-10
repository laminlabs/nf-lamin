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

import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.script.WorkflowMetadata

import ai.lamin.lamin_api_client.ApiException
import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.hub.LaminHubConfigResolver
import ai.lamin.nf_lamin.instance.Instance
import ai.lamin.nf_lamin.instance.InstanceSettings
import ai.lamin.nf_lamin.model.RunStatus

/**
 * Holds shared state about the currently active Lamin transform and run.
 *
 * The observer updates this singleton as the workflow lifecycle progresses,
 * while extension functions expose the captured metadata to Nextflow scripts.
 */
@Slf4j
@CompileStatic
final class LaminRunManager {

    private static final LaminRunManager INSTANCE = new LaminRunManager()

    private final Lock artifactLock = new ReentrantLock()

    private volatile Session session
    private volatile LaminConfig config
    private volatile Map<String, Object> resolvedConfig
    private volatile LaminHub hub
    private volatile Instance laminInstance
    private volatile Map<String, Object> transform
    private volatile Map<String, Object> run

    private LaminRunManager() {
    }

    static LaminRunManager getInstance() {
        return INSTANCE
    }

    synchronized void reset() {
        session = null
        config = null
        resolvedConfig = null
        hub = null
        laminInstance = null
        transform = null
        run = null
    }

    synchronized void updateTransform(Map<String, Object> data) {
        transform = data != null ? Collections.unmodifiableMap(new LinkedHashMap<String, Object>(data)) : null
    }

    Map<String, Object> getTransform() {
        return transform
    }

    synchronized void updateRun(Map<String, Object> data) {
        run = data != null ? Collections.unmodifiableMap(new LinkedHashMap<String, Object>(data)) : null
    }

    Map<String, Object> getRun() {
        return run
    }

    Instance getLaminInstance() {
        return laminInstance
    }

    synchronized void setLaminInstance(Instance instance) {
        laminInstance = instance
    }

    void initializeRunManager(Session session) {
        log.debug 'LaminRunManager.initializeRunManager'
        reset()
        this.session = session

        LaminConfig parsedConfig = LaminConfig.parseConfig(session)
        log.debug "Parsed config: ${parsedConfig.toString()}"
        config = parsedConfig

        Map<String, Object> resolved = LaminHubConfigResolver.resolve(parsedConfig)
        log.debug 'Resolved config with hub settings'
        resolvedConfig = resolved

        LaminHub newHub = new LaminHub(
            resolved.supabaseApiUrl as String,
            resolved.supabaseAnonKey as String,
            parsedConfig.apiKey
        )
        log.debug 'Created LaminHub client'
        hub = newHub

        InstanceSettings settings = newHub.getInstanceSettings(parsedConfig.instanceOwner, parsedConfig.instanceName)
        log.debug "Instance settings: ${settings.toString()}"

        Instance instance = new Instance(
            newHub,
            settings,
            parsedConfig.maxRetries,
            parsedConfig.retryDelay
        )
        laminInstance = instance

        testConnection()
    }

    void initializeRun() {
        log.debug 'LaminRunManager.initializeRun'
        fetchOrCreateTransform()
        fetchOrCreateRun()
    }

    void startRun() {
        log.debug 'LaminRunManager.startRun'
        Map<String, Object> currentRun = getRun()
        if (!currentRun || laminInstance == null || session == null) {
            return
        }

        WorkflowMetadata wfMetadata = session.getWorkflowMetadata()
        laminInstance.updateRecord(
            moduleName: 'core',
            modelName: 'run',
            uid: currentRun.get('uid') as String,
            data: [
                started_at: wfMetadata.start,
                _status_code: RunStatus.STARTED.code
            ]
        )

        Map<String, Object> updatedRun = new LinkedHashMap<String, Object>(currentRun)
        updatedRun.put('started_at', wfMetadata.start)
        updatedRun.put('_status_code', RunStatus.STARTED.code)
        updateRun(updatedRun)
        log.info "Run ${updatedRun.get('uid')} ${RunStatus.STARTED.description}"
    }

    void testConnection() {
        if (laminInstance == null) {
            return
        }
        String instanceString = "${laminInstance.getOwner()}/${laminInstance.getName()}"
        try {
            Map account = laminInstance.getAccount()
            log.info "→ connected lamindb: '${instanceString}' as '${account.handle}'"
        } catch (ApiException e) {
            log.error "✗ Could not connect lamindb: '${instanceString}'!"
            log.error 'API call failed: ' + e.getMessage()
        }
    }

    Map<String, Object> fetchOrCreateTransform() {
        ensureInitialized('fetchOrCreateTransform requires session, config, and instance to be initialised')

        if (config.transformUid) {
            log.debug "Using manually specified transform UID: ${config.transformUid}"
            try {
                Map transformRecord = laminInstance.getRecord(
                    moduleName: 'core',
                    modelName: 'transform',
                    idOrUid: config.transformUid
                )
                updateTransform(transformRecord)
                printTransformMessage(transformRecord, "Received transform ${transformRecord.get('uid')} from config")
                return transformRecord
            } catch (Exception e) {
                log.error "Failed to fetch transform with UID ${config.transformUid}: ${e.getMessage()}"
                log.warn 'Falling back to normal transform lookup/creation process'
            }
        }

        WorkflowMetadata wfMetadata = session.getWorkflowMetadata()
        String repository = wfMetadata.repository ?: wfMetadata.projectName
        String mainScript = wfMetadata.scriptFile.toString().replaceFirst("${wfMetadata.projectDir}/", '')
        String revision = wfMetadata.revision
        String key = mainScript == 'main.nf' ? repository : "${repository}:${mainScript}"

        log.debug "Searching for existing Transform with key ${key} and revision ${revision}"
        List<Map> existingTransforms = laminInstance.getRecords(
            moduleName: 'core',
            modelName: 'transform',
            filter: [
                and: [
                    [key: [eq: key]],
                    [version: [eq: revision]]
                ]
            ]
        )
        log.debug "Found ${existingTransforms.size()} existing Transform(s) with key ${key} and revision ${revision}"

        Map transformRecord = null
        if (existingTransforms) {
            if (existingTransforms.size() > 1) {
                log.warn "Found multiple Transform objects with key ${key} and revision ${revision}"
            }
            transformRecord = existingTransforms[0]
            updateTransform(transformRecord)
            printTransformMessage(transformRecord, "Using existing transform ${transformRecord.get('uid')}")
            return transformRecord
        }

        String description = "${wfMetadata.manifest.getName()}: ${wfMetadata.manifest.getDescription()}"
        String commitId = wfMetadata.commitId
        Map info = [
            'repository': repository,
            'main-script': mainScript,
            'commit-id': commitId,
            'revision': revision
        ]
        String infoAsJson = JsonOutput.toJson(info)

        transformRecord = laminInstance.createTransform(
            key: key,
            source_code: infoAsJson,
            version: revision,
            type: 'pipeline',
            reference: wfMetadata.repository,
            reference_type: 'url',
            description: description
        )
        updateTransform(transformRecord)
        printTransformMessage(transformRecord, "Created new transform ${transformRecord.get('uid')}")
        return transformRecord
    }

    Map<String, Object> fetchOrCreateRun() {
        ensureInitialized('fetchOrCreateRun requires transform and instance to be initialised')

        if (config.runUid) {
            log.debug "Using manually specified run UID: ${config.runUid}"
            try {
                Map runRecord = laminInstance.getRecord(
                    moduleName: 'core',
                    modelName: 'run',
                    idOrUid: config.runUid,
                    includeForeignKeys: true
                )

                Integer expectedTransformId = (transform?.get('id') as Number)?.intValue()
                Integer runTransformId = (runRecord.transform_id as Number)?.intValue()
                Integer statusCode = (runRecord._status_code as Number)?.intValue()
                if (expectedTransformId != runTransformId) {
                    log.warn "Run ${config.runUid} is associated with transform ${runTransformId} (expected ${expectedTransformId}). Creating a new run instead."
                } else if (statusCode != RunStatus.SCHEDULED.code) {
                    log.warn "Run ${config.runUid} has status code ${statusCode} (expected ${RunStatus.SCHEDULED.code} for SCHEDULED). Creating a new run instead."
                } else {
                    updateRun(runRecord)
                    printRunMessage(runRecord, "Received run ${runRecord.get('uid')} from config")
                    return runRecord
                }
            } catch (Exception e) {
                log.error "Failed to fetch run with UID ${config.runUid}: ${e.getMessage()}"
                log.warn 'Creating a new run instead'
            }
        }

        WorkflowMetadata wfMetadata = session.getWorkflowMetadata()
        Integer transformId = (transform?.get('id') as Number)?.intValue()
        Map runRecord = laminInstance.createRecord(
            moduleName: 'core',
            modelName: 'run',
            data: [
                transform_id: transformId,
                name: wfMetadata.runName,
                created_at: wfMetadata.start,
                started_at: wfMetadata.start,
                _status_code: RunStatus.SCHEDULED.code
            ]
        )
        updateRun(runRecord)
        printRunMessage(runRecord, 'Created new run')
        return runRecord
    }

    void finalizeRun(RunStatus status) {
        if (run == null || laminInstance == null || session == null) {
            return
        }
        log.info "Run ${run.get('uid')} ${status.description}"
        WorkflowMetadata wfMetadata = session.getWorkflowMetadata()
        laminInstance.updateRecord(
            moduleName: 'core',
            modelName: 'run',
            uid: run.get('uid') as String,
            data: [
                finished_at: wfMetadata.complete,
                _status_code: status.code
            ]
        )

        Map<String, Object> updatedRun = new LinkedHashMap<String, Object>(run)
        updatedRun.put('finished_at', wfMetadata.complete)
        updatedRun.put('_status_code', status.code)
        updateRun(updatedRun)
    }

    Map<String, Object> fetchOrCreateStorage(Path path) {
        if (laminInstance == null) {
            return null
        }
        String root = path.getFileSystem().toString()
        URI uri = path.toUri()
        String type = uri.getScheme()

        List<Map> existingStorage = laminInstance.getRecords(
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
            storage = laminInstance.createRecord(
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

    Map<String, Object> createOutputArtifact(Path path) {
        Map<String, Object> currentRun = run
        if (currentRun == null || laminInstance == null) {
            return null
        }

        boolean isLocalFile = (path.toUri().getScheme() ?: 'file') == 'file'
        Integer runId = (currentRun.get('id') as Number)?.intValue()
        if (runId == null) {
            return null
        }
        String description = "Output artifact for run ${runId}"

        log.debug "Creating output artifact for run ${runId} at ${path.toUri()}"

        Map<String, Object> artifact = null
        artifactLock.lock()
        try {
            if (isLocalFile) {
                File file = path.toFile()
                artifact = laminInstance.uploadArtifact(
                    file: file,
                    run_id: runId,
                    description: description
                )
            } else {
                String remotePath = path.toUri().toString()
                artifact = laminInstance.createArtifact(
                    path: remotePath,
                    run_id: runId,
                    description: description
                )
            }
        } catch (Exception e) {
            log.error "Failed to create output artifact for run ${runId} at ${path.toUri()}"
            log.debug "Exception: ${e.getMessage()}", e
            return null
        } finally {
            artifactLock.unlock()
        }

        Number artifactRunNumber = ((artifact.get('run') ?: artifact.get('run_id')) as Number)
        boolean isNewArtifact = artifactRunNumber != null && artifactRunNumber.intValue() == runId
        String verb = isNewArtifact ? (isLocalFile ? 'Uploaded' : 'Created') : 'Detected previous'
        String webUrl = resolvedConfig != null ? resolvedConfig.get('webUrl') as String : null
        String owner = laminInstance.getOwner()
        String name = laminInstance.getName()
        String artifactUid = artifact.get('uid') as String
        if (webUrl) {
            log.debug "${verb} output artifact ${artifactUid} (${webUrl}/${owner}/${name}/artifact/${artifactUid})"
        } else {
            log.debug "${verb} output artifact ${artifactUid}"
        }
        return artifact
    }

    private void printTransformMessage(Map transformRecord, String message) {
        String webUrl = resolvedConfig != null ? resolvedConfig.get('webUrl') as String : null
        String owner = laminInstance != null ? laminInstance.getOwner() : null
        String name = laminInstance != null ? laminInstance.getName() : null
        String transformUid = transformRecord.get('uid') as String
        if (webUrl && owner && name && transformUid) {
            log.info "${message} (${webUrl}/${owner}/${name}/transform/${transformUid})"
        } else {
            log.info message
        }
    }

    private void printRunMessage(Map runRecord, String message) {
        String webUrl = resolvedConfig != null ? resolvedConfig.get('webUrl') as String : null
        String owner = laminInstance != null ? laminInstance.getOwner() : null
        String name = laminInstance != null ? laminInstance.getName() : null
        String transformUid = transform != null ? transform.get('uid') as String : null
        String runUid = runRecord.get('uid') as String
        if (webUrl && owner && name && transformUid && runUid) {
            log.info "${message} (${webUrl}/${owner}/${name}/transform/${transformUid}/${runUid})"
        } else {
            log.info message
        }
    }

    private void ensureInitialized(String message) {
        if (session == null || config == null || laminInstance == null) {
            throw new IllegalStateException(message)
        }
    }
}
