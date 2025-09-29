package ai.lamin.nf_lamin

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

import nextflow.Session
import nextflow.config.Manifest
import nextflow.script.WorkflowMetadata
import spock.lang.Requires
import spock.lang.Specification

import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.hub.LaminHubConfigResolver
import ai.lamin.nf_lamin.instance.Instance
import ai.lamin.nf_lamin.instance.InstanceSettings
import ai.lamin.nf_lamin.model.RunStatus

/**
 * Integration tests that exercise the Lamin observer against real LaminDB instances.
 */
class LaminObserverIntegrationTest extends Specification {

    private static final String INSTANCE_NAME = 'laminlabs/lamindata'

    private static boolean hasEnvVars(Collection<String> names) {
        names.every { System.getenv(it)?.trim() }
    }

    private LaminConfig buildConfig(String apiKey, String envName, Map<String, Object> overrides = [:]) {
        Map<String, Object> options = [
            instance: INSTANCE_NAME,
            api_key: apiKey,
            env: envName
        ]
        overrides.each { String key, Object value -> options[key] = value }
        return new LaminConfig(options)
    }

    private Instance createInstance(LaminConfig config) {
        Map<String, Object> resolved = LaminHubConfigResolver.resolve(config)
        LaminHub hub = new LaminHub(
            resolved.supabaseApiUrl as String,
            resolved.supabaseAnonKey as String,
            config.apiKey
        )
        InstanceSettings settings = hub.getInstanceSettings(config.instanceOwner, config.instanceName)
        return new Instance(hub, settings, config.maxRetries, config.retryDelay)
    }

    private WorkflowMetadata buildMetadata(Map<String, Object> overrides = [:]) {
        OffsetDateTime start = (overrides.start as OffsetDateTime) ?: OffsetDateTime.now().minusMinutes(5)
        OffsetDateTime complete = (overrides.complete as OffsetDateTime) ?: start.plusMinutes(3)
        String repository = overrides.repository as String ?: 'https://github.com/nf-core/scrnaseq'
        Path projectDir = (overrides.projectDir as Path) ?: Paths.get('/opt/workflows/nf-lamin-test')
        Path scriptFile = (overrides.scriptFile as Path) ?: projectDir.resolve(overrides.scriptName as String ?: 'main.nf')
        Manifest manifest = Stub(Manifest) {
            getName() >> (overrides.manifestName as String ?: 'nf-lamin integration test')
            getDescription() >> (overrides.manifestDescription as String ?: 'Integration test execution')
        }

        return Stub(WorkflowMetadata) {
            getRepository() >> repository
            getProjectName() >> (overrides.projectName as String ?: 'nf-lamin/test')
            getScriptFile() >> scriptFile
            getProjectDir() >> projectDir
            getRevision() >> (overrides.revision as String ?: 'main')
            getManifest() >> manifest
            getCommitId() >> (overrides.commitId as String ?: 'abcdef1234567890')
            getRunName() >> (overrides.runName as String ?: "nf-lamin-integration-${UUID.randomUUID().toString().substring(0, 8)}")
            getStart() >> start
            getComplete() >> complete
        }
    }

    private Map<String, Object> waitForRunStatus(Instance instance, String runUid, int expectedStatus, long timeoutMillis = TimeUnit.SECONDS.toMillis(15)) {
        long startTime = System.currentTimeMillis()
        Map<String, Object> run
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            run = instance.getRecord(
                moduleName: 'core',
                modelName: 'run',
                idOrUid: runUid,
                includeForeignKeys: true
            )
            if ((run?._status_code ?: run?.get('_status_code')) == expectedStatus) {
                return run
            }
            sleep(500)
        }
        throw new AssertionError("Run ${runUid} did not reach status ${expectedStatus} within ${timeoutMillis}ms (last status: ${run?._status_code})")
    }

    @Requires({ LaminObserverIntegrationTest.hasEnvVars(['LAMIN_API_KEY']) })
    def "should respect manual transform and run overrides in prod"() {
        given:
        String apiKey = System.getenv('LAMIN_API_KEY')
        LaminConfig config = buildConfig(apiKey, 'prod', [transform_uid: 'PhX5TXQhj3l6wowA'])
        Instance apiClient = createInstance(config)

        Map<String, Object> transform = apiClient.getRecord(
            moduleName: 'core',
            modelName: 'transform',
            idOrUid: config.transformUid
        )
        assert transform?.id : 'Expected manual transform to exist in LaminDB'

        String runName = "nf-lamin-manual-${UUID.randomUUID().toString().substring(0, 8)}"
        OffsetDateTime runStart = OffsetDateTime.now().minusMinutes(2)
        Map<String, Object> manualRun = apiClient.createRecord(
            moduleName: 'core',
            modelName: 'run',
            data: [
                transform_id: transform.id,
                name: runName,
                created_at: runStart,
                started_at: runStart,
                _status_code: RunStatus.SCHEDULED.code
            ]
        )

        Map<String, Object> sessionConfig = [
            instance: INSTANCE_NAME,
            api_key: apiKey,
            env: 'prod',
            transform_uid: transform.uid,
            run_uid: manualRun.uid
        ]

        WorkflowMetadata metadata = buildMetadata([
            runName: runName,
            start: runStart,
            complete: runStart.plusMinutes(5),
            repository: transform.reference ?: 'https://github.com/nf-core/scrnaseq'
        ])

        Session session = Stub(Session) {
            getConfig() >> [lamin: sessionConfig]
            getWorkflowMetadata() >> metadata
        }

        LaminObserver observer = new LaminObserver()

        when:
        observer.onFlowCreate(session)

        then:
        observer.@transform.uid == transform.uid
        observer.@run.uid == manualRun.uid
        apiClient.getRecord(
            moduleName: 'core',
            modelName: 'run',
            idOrUid: manualRun.uid
        )._status_code == RunStatus.SCHEDULED.code

        when:
        observer.onFlowBegin()
        Map<String, Object> runAfterBegin = waitForRunStatus(apiClient, manualRun.uid as String, RunStatus.STARTED.code)

        then:
        runAfterBegin.started_at != null

        when:
        observer.onFlowComplete()
        Map<String, Object> runAfterComplete = waitForRunStatus(apiClient, manualRun.uid as String, RunStatus.COMPLETED.code)

        then:
        runAfterComplete.finished_at != null
    }

    @Requires({ LaminObserverIntegrationTest.hasEnvVars(['LAMIN_STAGING_API_KEY', 'LAMIN_TEST_BUCKET']) })
    def "should create new records and register artifacts in staging"() {
        given:
        String apiKey = System.getenv('LAMIN_STAGING_API_KEY')
        String bucket = System.getenv('LAMIN_TEST_BUCKET')
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8)
        String revision = "integration-${uniqueSuffix}"
        String runName = "nf-lamin-auto-${uniqueSuffix}"
        Path projectDir = Paths.get("/opt/workflows/integration-${uniqueSuffix}")
        Path scriptPath = projectDir.resolve('custom.nf')
        OffsetDateTime runStart = OffsetDateTime.now().minusMinutes(3)

        LaminConfig config = buildConfig(apiKey, 'staging')
        Instance apiClient = createInstance(config)

        Map<String, Object> sessionConfig = [
            instance: INSTANCE_NAME,
            api_key: apiKey,
            env: 'staging'
        ]

        WorkflowMetadata metadata = buildMetadata([
            repository: 'https://github.com/laminlabs/nf-lamin',
            projectName: 'laminlabs/nf-lamin',
            revision: revision,
            runName: runName,
            projectDir: projectDir,
            scriptFile: scriptPath,
            start: runStart,
            complete: runStart.plusMinutes(10)
        ])

        Session session = Stub(Session) {
            getConfig() >> [lamin: sessionConfig]
            getWorkflowMetadata() >> metadata
        }

        LaminObserver observer = new LaminObserver()

        when:
        observer.onFlowCreate(session)

        then:
        observer.@transform.uid
        observer.@run.uid
        waitForRunStatus(apiClient, observer.@run.uid as String, RunStatus.SCHEDULED.code)

        when: 'we spy on the instance to capture artifact creation and start the run'
        Instance instanceSpy = Spy(observer.@instance)
        Map<String, Object> uploadedArtifact = null
        Map<String, Object> remoteArtifact = null
        instanceSpy.uploadArtifact(_ as Map) >> { Map<String, Object> args ->
            Map<String, Object> result = callRealMethod()
            uploadedArtifact = result
            return result
        }
        instanceSpy.createArtifact(_ as Map) >> { Map<String, Object> args ->
            Map<String, Object> result = callRealMethod()
            remoteArtifact = result
            return result
        }
        observer.@instance = instanceSpy
        observer.onFlowBegin()
        waitForRunStatus(apiClient, observer.@run.uid as String, RunStatus.STARTED.code)

        and: 'a local artifact is published'
        Path localFile = Files.createTempFile("nf-lamin-local-${uniqueSuffix}", '.txt')
        Files.writeString(localFile, 'nf-lamin integration test artifact')
        observer.onFilePublish(localFile, localFile)

        then:
        uploadedArtifact?.uid
        (uploadedArtifact.run ?: uploadedArtifact.run_id) == observer.@run.id

        when: 'a remote artifact URI is registered'
        URI remoteUri = new URI("s3://${bucket}/nf-lamin/${uniqueSuffix}.txt")
        Path remotePath = Stub(Path) {
            toUri() >> remoteUri
            toString() >> remoteUri.toString()
        }
        observer.onFilePublish(remotePath, localFile)

        then:
        remoteArtifact?.uid
        remoteArtifact.path == remoteUri.toString()
        (remoteArtifact.run ?: remoteArtifact.run_id) == observer.@run.id

        when:
        observer.onFlowComplete()
        Map<String, Object> completed = waitForRunStatus(apiClient, observer.@run.uid as String, RunStatus.COMPLETED.code)

        then:
        completed.finished_at != null
    }
}
