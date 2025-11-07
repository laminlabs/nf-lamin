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
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.file.FileHelper
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

        if (config.dryRun) {
            log.info 'nf-lamin: Dry-run mode enabled'
            return
        }

        try {
            fetchOrCreateTransform()
            fetchOrCreateRun()
        } catch (Exception e) {
            log.error 'Failed to initialize run', e
            throw e
        }
    }

    void startRun() {
        log.debug 'LaminRunManager.startRun'
        if (run == null || laminInstance == null || session == null || config.dryRun) {
            return
        }

        WorkflowMetadata wfMetadata = session.getWorkflowMetadata()
        Map<String, Object> updatedRun = laminInstance.updateRecord(
            moduleName: 'core',
            modelName: 'run',
            uid: run.get('uid') as String,
            data: [
                started_at: wfMetadata.start,
                _status_code: RunStatus.STARTED.code
            ]
        )
        if (run.uid != updatedRun.uid) {
            log.warn "Run UID changed from ${run.uid} to ${updatedRun.uid} on start update!"
        }
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
        String revision = wfMetadata.revision ?: 'local-development'
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

        if (config.dryRun) {
            // return a dummy object and print a message
            transformRecord = [
                uid: 'DrYrUnTrAuId',
                id: -1,
                key: key,
                version: revision
            ] as Map<String, Object>
            updateTransform(transformRecord)
            log.info "Dry-run mode: using dummy transform ${transformRecord.get('uid')}"
            return transformRecord
        }

        String manifestName = wfMetadata.manifest.getName() ?: '<No name in manifest>'
        String manifestDescription = wfMetadata.manifest.getDescription() ?: '<No description in manifest>'
        String description = "${manifestName}: ${manifestDescription}"
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

        if (config.dryRun) {
            Map<String, Object> dummyRunRecord = [
                uid: 'DrYrUnRuNuId',
                id: -1,
                transform_id: (transform?.get('id') as Number)?.intValue(),
                _status_code: RunStatus.SCHEDULED.code
            ] as Map<String, Object>
            updateRun(dummyRunRecord)
            log.info "Dry-run mode: created dummy run ${dummyRunRecord.get('uid')}"
        }

        WorkflowMetadata wfMetadata = session.getWorkflowMetadata()
        Integer transformId = (transform?.get('id') as Number)?.intValue()
        Map<String, Object> runRecord = laminInstance.createRecord(
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

    void finalizeRun() {
        if (run == null || laminInstance == null || session == null || config.dryRun) {
            return
        }

        RunStatus status = session.isSuccess() ? RunStatus.COMPLETED : RunStatus.ERRORED

        log.info "Run ${run.get('uid')} ${status.description}"
        WorkflowMetadata wfMetadata = session.getWorkflowMetadata()

        // Update run with finish time, status, and report artifact
        Map<String, Object> updateData = [
            finished_at: wfMetadata.complete,
            _status_code: status.code
        ]

        // Handle report artifact
        Integer reportArtifactId = createReportArtifact()
        if (reportArtifactId != null) {
            updateData.put('report_id', reportArtifactId)
        }

        Map<String, Object> updatedRun = laminInstance.updateRecord(
            moduleName: 'core',
            modelName: 'run',
            uid: run.get('uid') as String,
            data: updateData
        )
        if (run.uid != updatedRun.uid) {
            log.warn "Run UID changed from ${run.uid} to ${updatedRun.uid} on final update!"
        }
        updateRun(updatedRun)
    }

    // todo: how link to current run
    Map<String, Object> createInputArtifact(Path path) {
        if (laminInstance == null || config.dryRun) {
            return null
        }

        // if path is a local file, skip creating input artifact
        boolean isLocalFile = (path.toUri().getScheme() ?: 'file') == 'file'
        if (isLocalFile) {
            return null
        }

        String description = "Input artifact at ${path.toUri()}"

        Map<String, Object> artifact = createOrUploadArtifact(
            path: path,
            description: description
        )

        log.info "Created input artifact ${artifact?.get('uid')} for path ${path.toUri()}. Data: ${artifact}"

        // todo: link artifact to current run as an input artifact

        return artifact
    }

    Map<String, Object> createOutputArtifact(Path path) {
        if (run == null || laminInstance == null || config.dryRun) {
            return null
        }

        Integer runId = (run.get('id') as Number)?.intValue()
        if (runId == null) {
            return null
        }

        String description = "Output artifact for run ${runId}"
        return createOrUploadArtifact(
            path: path,
            run_id: runId,
            description: description
        )
    }

    Map<String, Object> createOrUploadArtifact(Map<String, Object> params) {
        if (laminInstance == null || config.dryRun) {
            return null
        }

        // Validate and extract required parameter
        if (!params.get('path')) {
            throw new IllegalArgumentException("Required parameter 'path' is missing")
        }

        Path path = params.get('path') as Path
        if (path == null) {
            throw new IllegalArgumentException("Parameter 'path' must be a valid Path object")
        }

        // Validate and extract optional parameters
        Integer runId = null
        if (params.containsKey('run_id')) {
            Object runIdValue = params.get('run_id')
            if (runIdValue != null && !(runIdValue instanceof Integer)) {
                throw new IllegalArgumentException("Parameter 'run_id' must be an Integer or null")
            }
            runId = runIdValue as Integer
        }

        String description = null
        if (params.containsKey('description')) {
            Object descValue = params.get('description')
            if (descValue != null && !(descValue instanceof String)) {
                throw new IllegalArgumentException("Parameter 'description' must be a String or null")
            }
            description = descValue as String
        }

        String kind = null
        if (params.containsKey('kind')) {
            Object kindValue = params.get('kind')
            if (kindValue != null && !(kindValue instanceof String)) {
                throw new IllegalArgumentException("Parameter 'kind' must be a String or null")
            }
            kind = kindValue as String
        }

        boolean isLocalFile = (path.toUri().getScheme() ?: 'file') == 'file'

        String logContext = runId != null ? "for run ${runId}" : "without run association"
        log.debug "Creating artifact ${logContext} at ${path.toUri()}"

        Map<String, Object> artifact = null
        artifactLock.lock()
        try {
            Map<String, Object> apiParams = [:]
            if (runId != null) {
                apiParams.put('run_id', runId)
            }
            if (description != null) {
                apiParams.put('description', description)
            }
            if (kind != null) {
                apiParams.put('kind', kind)
            }

            if (isLocalFile) {
                File file = path.toFile()
                apiParams.put('file', file)
                artifact = laminInstance.uploadArtifact(apiParams)
            } else {
                String remotePath = path.toUri().toString()
                apiParams.put('path', remotePath)
                artifact = laminInstance.createArtifact(apiParams)
            }
        } catch (Exception e) {
            log.error "Failed to create artifact ${logContext} at ${path.toUri()}"
            log.debug "Exception: ${e.getMessage()}", e
            return null
        } finally {
            artifactLock.unlock()
        }

        Number artifactRunNumber = ((artifact.get('run') ?: artifact.get('run_id')) as Number)
        boolean isNewArtifact = runId == null || (artifactRunNumber != null && artifactRunNumber.intValue() == runId)
        String verb = isNewArtifact ? (isLocalFile ? 'Uploaded' : 'Created') : 'Detected previous'
        String webUrl = resolvedConfig != null ? resolvedConfig.get('webUrl') as String : null
        String owner = laminInstance.getOwner()
        String name = laminInstance.getName()
        String artifactUid = artifact.get('uid') as String
        if (webUrl) {
            log.debug "${verb} artifact ${artifactUid} (${webUrl}/${owner}/${name}/artifact/${artifactUid})"
        } else {
            log.debug "${verb} artifact ${artifactUid}"
        }
        return artifact
    }

    private Integer createReportArtifact() {
        Map reportConfig = session.config.navigate("report") as Map
        log.debug "Report config: ${reportConfig}"

        // Determine the report path (either existing file or generate placeholder)
        boolean reportEnabled = reportConfig?.get('enabled') as Boolean ?: false
        boolean hasReportFile = reportEnabled && reportConfig?.get('file')

        Path reportPath = null
        Path tempReportPath = null
        boolean isPlaceholder = false

        if (hasReportFile) {
            // Use existing report file
            reportPath = FileHelper.asPath(reportConfig.get('file') as String)
            log.debug "Report enabled, using file: ${reportPath}"
        } else {
            // Generate placeholder HTML
            log.debug "Report not enabled, generating placeholder HTML"
            tempReportPath = Files.createTempFile("nextflow-report-", ".html")
            reportPath = tempReportPath
            isPlaceholder = true

            String placeholderHtml = """<!DOCTYPE html>
<html>
<head>
    <title>Nextflow Report Not Generated</title>
    <style>
        body { font-family: Arial, sans-serif; max-width: 800px; margin: 50px auto; padding: 20px; }
        .info-box { background: #f0f0f0; border-left: 4px solid #007acc; padding: 20px; }
        code { background: #e8e8e8; padding: 2px 6px; border-radius: 3px; }
        pre { background: #f5f5f5; padding: 15px; border-radius: 5px; overflow-x: auto; }
    </style>
</head>
<body>
    <h1>Nextflow Execution Report Not Generated</h1>
    <div class="info-box">
        <p>To generate an execution report for your Nextflow workflow, you can either:</p>
        <p><strong>Option 1:</strong> Enable the report in your <code>nextflow.config</code>:</p>
        <pre>report {
    enabled = true
    file = "path/to/lamin_report-\${new Date().format('yyyyMMdd-HHmmss')}.html"
}</pre>
        <p><strong>Option 2:</strong> Add the <code>-with-report</code> flag to your nextflow run command:</p>
        <pre>nextflow run your_pipeline.nf -with-report</pre>
        <p>For more information, see the <a href="https://www.nextflow.io/docs/latest/reports.html#execution-report" target="_blank">Nextflow documentation</a>.</p>
    </div>
</body>
</html>"""
            Files.write(reportPath, placeholderHtml.getBytes('UTF-8'))
        }

        // Create artifact from the report path
        try {
            String description = "Nextflow execution report for run ${run?.get('uid')}"

            Map<String, Object> artifact = createOrUploadArtifact(
                path: reportPath,
                run_id: null,
                description: description,
                kind: "__lamindb_run__"
            )

            if (artifact) {
                Integer artifactId = (artifact.get('id') as Number)?.intValue()
                String artifactUid = artifact.get('uid') as String

                // If id is missing, fetch it using the uid
                if (artifactId == null && artifactUid != null) {
                    log.debug "Artifact ID missing from API response, looking up artifact by UID: ${artifactUid}"
                    try {
                        Map<String, Object> fetchedArtifact = laminInstance.getRecord(
                            moduleName: 'core',
                            modelName: 'artifact',
                            idOrUid: artifactUid
                        )
                        artifactId = (fetchedArtifact.get('id') as Number)?.intValue()
                    } catch (Exception e) {
                        log.warn "Failed to fetch artifact ID for UID ${artifactUid}: ${e.getMessage()}"
                    }
                }

                if (isPlaceholder) {
                    log.debug "Created placeholder report artifact ${artifactUid}"
                } else {
                    log.info "Created report artifact ${artifactUid}"
                }
                return artifactId
            }
            return null
        } finally {
            // Clean up temp file if we created one
            if (tempReportPath != null) {
                try {
                    Files.delete(tempReportPath)
                } catch (Exception e) {
                    log.debug "Failed to delete temporary report file: ${e.getMessage()}"
                }
            }
        }
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
