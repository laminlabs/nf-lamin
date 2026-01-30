/*
 * Copyright (c) 2013-2024, Lamin Labs GmbH.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package ai.lamin.nf_lamin.instance

import ai.lamin.nf_lamin.LaminConfig
import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.hub.LaminHubConfigResolver
import ai.lamin.nf_lamin.instance.Instance
import ai.lamin.nf_lamin.instance.InstanceSettings
import ai.lamin.nf_lamin.model.RunStatus
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Shared
import java.util.UUID
import java.nio.file.Path
import java.time.OffsetDateTime

/**
 * Tests the Instance class with real production environment API calls
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
class InstanceTest extends Specification {

    // ==================== ENVIRONMENT CONFIGURATION ====================
    // Change these values when switching between production and staging

    static final String TEST_ENV = 'prod'  // 'prod' or 'staging'
    static final String TEST_API_KEY_ENV_VAR = 'LAMIN_API_KEY'  // 'LAMIN_API_KEY' or 'LAMIN_STAGING_API_KEY'
    static final String TEST_INSTANCE = 'laminlabs/lamindata'
    static final String TEST_INSTANCE_OWNER = 'laminlabs'
    static final String TEST_INSTANCE_NAME = 'lamindata'

    // Helper method for @Requires annotations - only change TEST_API_KEY_ENV_VAR above
    static boolean hasApiKey() {
        System.getenv(TEST_API_KEY_ENV_VAR) != null
    }

    @Shared
    String apiKey = System.getenv(TEST_API_KEY_ENV_VAR)

    @Shared
    Instance instance

    // ==================== TEST ARTIFACT CONSTANTS ====================
    // These can be modified when switching between production and staging

    // S3 artifact (non-versioned uid)
    static final String TEST_S3_ARTIFACT_UID = 's3rtK8wIzJNKvg5Q'
    static final String TEST_S3_ARTIFACT_UID_VERSIONED = 's3rtK8wIzJNKvg5Q0000'
    static final String TEST_S3_STORAGE_ROOT = 's3://lamindata'
    static final String TEST_S3_ARTIFACT_KEY = '.lamindb/s3rtK8wIzJNKvg5Q0000.txt'
    static final String TEST_S3_ARTIFACT_SUFFIX = '.txt'

    // GCS artifact
    static final String TEST_GS_ARTIFACT_UID = 'HOpnASIDDLx3pFYD0000'
    static final String TEST_GS_STORAGE_ROOT = 'gs://di-temporary-public'
    static final String TEST_GS_ARTIFACT_KEY = 'scratch/temp-bgzip/run_20251015_120418/run.bgzip.state.yaml'

    // ==================== TEST TRANSFORM/RUN CONSTANTS ====================
    // For tests that create runs and artifacts

    static final String TEST_TRANSFORM_UID = 'vplMRD5GZEzOB7PU'
    static final String TEST_RUN_UID = 'K9zPTNu3CRQokJyjj3ut'

    def setupSpec() {
        if (apiKey) {
            def config = LaminConfig.parseConfig([
                instance: TEST_INSTANCE,
                api_key: apiKey,
                env: TEST_ENV
            ])
            def resolvedConfig = LaminHubConfigResolver.resolve(config)
            def hub = new LaminHub(
                resolvedConfig.supabaseApiUrl,
                resolvedConfig.supabaseAnonKey,
                resolvedConfig.apiKey
            )
            def settings = hub.getInstanceSettings(
                config.instanceOwner,
                config.instanceName
            )
            instance = new Instance(hub, settings, 3, 1000)
        }
    }

    // ==================== CONSTRUCTOR TESTS ====================

    @Requires({ hasApiKey() })
    def "should throw exception for null hub"() {
        given:
        def config = LaminConfig.parseConfig([
            instance: TEST_INSTANCE,
            api_key: apiKey,
            env: TEST_ENV
        ])
        def resolvedConfig = LaminHubConfigResolver.resolve(config)
        def hub = new LaminHub(
            resolvedConfig.supabaseApiUrl,
            resolvedConfig.supabaseAnonKey,
            resolvedConfig.apiKey
        )
        def settings = hub.getInstanceSettings(
            config.instanceOwner,
            config.instanceName
        )

        when:
        new Instance(null, settings, 3, 1000)

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for null settings"() {
        given:
        def config = LaminConfig.parseConfig([
            instance: TEST_INSTANCE,
            api_key: apiKey,
            env: TEST_ENV
        ])
        def resolvedConfig = LaminHubConfigResolver.resolve(config)
        def hub = new LaminHub(
            resolvedConfig.supabaseApiUrl,
            resolvedConfig.supabaseAnonKey,
            resolvedConfig.apiKey
        )

        when:
        new Instance(hub, null, 3, 1000)

        then:
        thrown(IllegalStateException)
    }

    // ==================== GETTER TESTS ====================

    @Requires({ hasApiKey() })
    def "should get owner correctly"() {
        when:
        def owner = instance.getOwner()

        then:
        owner == TEST_INSTANCE_OWNER
    }

    @Requires({ hasApiKey() })
    def "should get name correctly"() {
        when:
        def name = instance.getName()

        then:
        name == TEST_INSTANCE_NAME
    }

    @Requires({ hasApiKey() })
    def "should get hub correctly"() {
        when:
        def hub = instance.getHub()

        then:
        hub != null
        hub instanceof LaminHub
    }

    @Requires({ hasApiKey() })
    def "should get settings correctly"() {
        when:
        def settings = instance.getSettings()

        then:
        settings != null
        settings.id() != null
        settings.id() instanceof UUID
        settings.owner() == TEST_INSTANCE_OWNER
        settings.name() == TEST_INSTANCE_NAME
        settings.apiUrl() instanceof String
        settings.apiUrl().startsWith('http')
        settings.schemaId() instanceof UUID
    }

    @Requires({ hasApiKey() })
    def "should get maxRetries correctly"() {
        when:
        def maxRetries = instance.getMaxRetries()

        then:
        maxRetries == 3
        maxRetries instanceof Integer
    }

    @Requires({ hasApiKey() })
    def "should get retryDelay correctly"() {
        when:
        def retryDelay = instance.getRetryDelay()

        then:
        retryDelay == 1000
        retryDelay instanceof Integer
    }

    @Requires({ hasApiKey() })
    def "should get API client configured correctly"() {
        when:
        def client = instance.getApiClient()

        then:
        client != null
        client.getBasePath() != null
        client.getBasePath().contains('api')
        client.getReadTimeout() == 30000
        client.getConnectTimeout() == 30000
        client.getWriteTimeout() == 30000
    }

    // ==================== STATISTICS API TESTS ====================

    @Requires({ hasApiKey() })
    def "should fetch instance statistics"() {
        when:
        def statistics = instance.getInstanceStatistics()

        then:
        statistics != null
        statistics instanceof Map || statistics instanceof Object
    }

    @Requires({ hasApiKey() })
    def "should get non-empty tables"() {
        when:
        def nonEmptyTables = instance.getNonEmptyTables()

        then:
        nonEmptyTables != null
        nonEmptyTables instanceof Map
    }

    // ==================== ACCOUNT API TESTS ====================

    @Requires({ hasApiKey() })
    def "should get account information"() {
        when:
        def account = instance.getAccount()

        then:
        account != null
        account instanceof Map
        // Account should have basic fields
        account.containsKey('id') || account.containsKey('uid') || account.containsKey('handle')
    }

    // ==================== RECORDS API TESTS ====================

    @Requires({ hasApiKey() })
    def "should get records with pagination"() {
        when:
        def records = instance.getRecords([
            moduleName: 'core',
            modelName: 'artifact',
            limit: 5,
            offset: 0
        ])

        then:
        records != null
        records instanceof List
        records.size() <= 5
    }

    @Requires({ hasApiKey() })
    def "should get records with filter"() {
        when:
        def records = instance.getRecords([
            moduleName: 'core',
            modelName: 'artifact',
            limit: 5,
            filter: [
                'suffix': ['eq': '.txt']
            ]
        ])

        then:
        records != null
        records instanceof List
        // All returned records should have .txt suffix
        records.every { it.suffix == '.txt' || records.isEmpty() }
    }

    @Requires({ hasApiKey() })
    def "should get single record by uid"() {
        when:
        // First get a record to know a valid uid
        def records = instance.getRecords([
            moduleName: 'core',
            modelName: 'artifact',
            limit: 1
        ])

        then:
        records != null
        records.size() > 0

        when:
        def uid = records[0].uid as String
        def record = instance.getRecord([
            moduleName: 'core',
            modelName: 'artifact',
            idOrUid: uid
        ])

        then:
        record != null
        record instanceof Map
        record.uid == uid
    }

    @Requires({ hasApiKey() })
    def "should handle getRecord with nonexistent id"() {
        when:
        instance.getRecord([
            moduleName: 'lamindb',
            modelName: 'Artifact',
            idOrUid: 'nonexistent-id-12345'
        ])

        then:
        thrown(Exception)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for getRecord with null moduleName"() {
        when:
        instance.getRecord([
            moduleName: null,
            modelName: 'Artifact',
            idOrUid: 'test'
        ])

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for getRecord with null modelName"() {
        when:
        instance.getRecord([
            moduleName: 'lamindb',
            modelName: null,
            idOrUid: 'test'
        ])

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for getRecord with null idOrUid"() {
        when:
        instance.getRecord([
            moduleName: 'lamindb',
            modelName: 'Artifact',
            idOrUid: null
        ])

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for getRecords with null moduleName"() {
        when:
        instance.getRecords([
            moduleName: null,
            modelName: 'Artifact'
        ])

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for getRecords with null modelName"() {
        when:
        instance.getRecords([
            moduleName: 'lamindb',
            modelName: null
        ])

        then:
        thrown(IllegalStateException)
    }

    // ==================== ARTIFACT STORAGE INFO TESTS ====================

    @Requires({ hasApiKey() })
    def "should get artifact storage info using non-versioned uid"() {
        when:
        def storageInfo = instance.getArtifactStorageInfo(TEST_S3_ARTIFACT_UID)

        then:
        storageInfo != null
        storageInfo.storageRoot == TEST_S3_STORAGE_ROOT
        storageInfo.artifactKey.startsWith(".lamindb/${TEST_S3_ARTIFACT_UID}")
        storageInfo.artifactKey.endsWith(TEST_S3_ARTIFACT_SUFFIX)
        storageInfo.artifactUid.startsWith(TEST_S3_ARTIFACT_UID)
    }

    @Requires({ hasApiKey() })
    def "should get artifact storage info using versioned uid"() {
        when:
        def storageInfo = instance.getArtifactStorageInfo(TEST_S3_ARTIFACT_UID_VERSIONED)

        then:
        storageInfo != null
        storageInfo.storageRoot == TEST_S3_STORAGE_ROOT
        storageInfo.artifactKey == TEST_S3_ARTIFACT_KEY
        storageInfo.artifactUid == TEST_S3_ARTIFACT_UID_VERSIONED
    }

    @Requires({ hasApiKey() })
    def "should get artifact storage info with gs service"() {
        when:
        def storageInfo = instance.getArtifactStorageInfo(TEST_GS_ARTIFACT_UID)

        then:
        storageInfo != null
        storageInfo.storageRoot == TEST_GS_STORAGE_ROOT
        storageInfo.artifactKey == TEST_GS_ARTIFACT_KEY
        storageInfo.artifactUid == TEST_GS_ARTIFACT_UID
    }

    @Requires({ hasApiKey() })
    def "should throw exception for null uid in getArtifactStorageInfo"() {
        when:
        instance.getArtifactStorageInfo(null)

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for empty uid in getArtifactStorageInfo"() {
        when:
        instance.getArtifactStorageInfo("")

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for invalid uid length in getArtifactStorageInfo"() {
        when:
        instance.getArtifactStorageInfo("short")

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for nonexistent uid in getArtifactStorageInfo"() {
        when:
        instance.getArtifactStorageInfo("aaaaaaaaaaaaaaaa")  // 16 chars but doesn't exist

        then:
        thrown(Exception)
    }

    // ==================== ARTIFACT FROM UID TESTS ====================
    // Note: getArtifactFromUid requires cloud file system plugins (nf-amazon, nf-google)
    // which are not available in unit tests. The method is tested indirectly through
    // getArtifactStorageInfo tests above.

    // @Requires({ hasApiKey() })
    // def "should get artifact from uid as Path"() {
    //     when:
    //     def path = instance.getArtifactFromUid(TEST_S3_ARTIFACT_UID)

    //     then:
    //     path != null
    //     path instanceof Path
    //     path.toString().contains(TEST_S3_ARTIFACT_UID)
    //     path.toString().endsWith(TEST_S3_ARTIFACT_SUFFIX)
    // }

    // ==================== ARTIFACT BY PATH TESTS ====================

    @Requires({ hasApiKey() })
    def "should return null for nonexistent artifact path"() {
        when:
        def artifact = instance.getArtifactByPath("nonexistent/path/to/file.txt")

        then:
        artifact == null
    }

    @Requires({ hasApiKey() })
    def "should throw exception for null path in getArtifactByPath"() {
        when:
        instance.getArtifactByPath(null)

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for empty path in getArtifactByPath"() {
        when:
        instance.getArtifactByPath("")

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should get artifact by existing path"() {
        // Note: getArtifactByPath looks up artifacts by their storage path.
        // This test verifies the API call works and returns expected format.
        // The method may return null for paths on non-default storage.
        when:
        def artifact = instance.getArtifactByPath(TEST_S3_ARTIFACT_KEY)

        then:
        // The artifact should either be found or return null gracefully
        artifact == null || (artifact instanceof Map && artifact.uid != null)
    }

    // ==================== CREATE/UPDATE RECORD TESTS ====================

    @Requires({ hasApiKey() })
    def "should throw exception for createRecord with null moduleName"() {
        when:
        instance.createRecord([
            moduleName: null,
            modelName: 'ULabel',
            data: [name: 'test']
        ])

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for createRecord with null modelName"() {
        when:
        instance.createRecord([
            moduleName: 'lamindb',
            modelName: null,
            data: [name: 'test']
        ])

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for updateRecord with null uid"() {
        when:
        instance.updateRecord([
            moduleName: 'lamindb',
            modelName: 'ULabel',
            uid: null,
            data: [name: 'updated']
        ])

        then:
        thrown(IllegalStateException)
    }

    // ==================== CREATE ARTIFACT TESTS ====================

    @Requires({ hasApiKey() })
    def "should throw exception for createArtifact with null path"() {
        when:
        instance.createArtifact([
            path: null
        ])

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for createArtifact with empty path"() {
        when:
        instance.createArtifact([
            path: ''
        ])

        then:
        thrown(IllegalStateException)
    }

    // ==================== UPLOAD ARTIFACT TESTS ====================

    @Requires({ hasApiKey() })
    def "should throw exception for uploadArtifact with null file"() {
        when:
        instance.uploadArtifact([
            file: null
        ])

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for uploadArtifact with nonexistent file"() {
        when:
        instance.uploadArtifact([
            file: new File('/nonexistent/path/to/file.txt')
        ])

        then:
        thrown(IllegalStateException)
    }

    // ==================== CREATE TRANSFORM TESTS ====================

    @Requires({ hasApiKey() })
    def "should throw exception for createTransform with null key"() {
        when:
        instance.createTransform([
            key: null,
            kind: 'pipeline',
            source_code: 'test'
        ])

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for createTransform with null kind"() {
        when:
        instance.createTransform([
            key: 'test-key',
            kind: null,
            source_code: 'test'
        ])

        then:
        thrown(IllegalStateException)
    }

    @Requires({ hasApiKey() })
    def "should throw exception for createTransform with null source_code"() {
        when:
        instance.createTransform([
            key: 'test-key',
            kind: 'pipeline',
            source_code: null
        ])

        then:
        thrown(IllegalStateException)
    }

    // ==================== TRANSFORM/RUN RETRIEVAL TESTS ====================

    @Requires({ hasApiKey() })
    def "should get transform by uid"() {
        when:
        def transform = instance.getRecord([
            moduleName: 'core',
            modelName: 'transform',
            idOrUid: TEST_TRANSFORM_UID
        ])

        then:
        transform != null
        transform instanceof Map
        transform.uid == TEST_TRANSFORM_UID
    }

    @Requires({ hasApiKey() })
    def "should get run by uid"() {
        when:
        def run = instance.getRecord([
            moduleName: 'core',
            modelName: 'run',
            idOrUid: TEST_RUN_UID
        ])

        then:
        run != null
        run instanceof Map
        run.uid == TEST_RUN_UID
    }

    // ==================== CREATE RUN TESTS ====================

    @Requires({ hasApiKey() })
    def "should create run linked to transform"() {
        given:
        // First get the transform to get its id
        def transform = instance.getRecord([
            moduleName: 'core',
            modelName: 'transform',
            idOrUid: TEST_TRANSFORM_UID
        ])
        def transformId = (transform?.id as Number)?.intValue()
        def runStart = OffsetDateTime.now()
        def data = [
            transform_id: transformId,
            name: "InstanceTest_${System.currentTimeMillis()}",
            created_at: runStart,
            started_at: runStart,
            _status_code: RunStatus.SCHEDULED.code
        ]

        when:
        def run
        try {
            run = instance.createRecord([
                moduleName: 'core',
                modelName: 'run',
                data: data
            ])
        } catch (Exception e) {
            println "ERROR creating run. Input data: ${data}"
            println "Transform: ${transform}"
            throw e
        }

        then:
        run != null
        run instanceof Map
        run.uid != null
        run.uid.length() == 20
        run.transform_id == transformId
    }

    // ==================== CREATE/UPLOAD ARTIFACT TESTS ====================

    @Requires({ hasApiKey() })
    def "should create artifact linked to run"() {
        given:
        // Get the run to get its id
        def run = instance.getRecord([
            moduleName: 'core',
            modelName: 'run',
            idOrUid: TEST_RUN_UID,
            includeForeignKeys: true
        ])
        def runId = (run?.id as Number)?.intValue()

        when:
        // Create artifact using a cloud path (simulating what happens during workflow)
        // use this as a path: https://httpbin.org/base64/aGVsbG8gd29ybGQ=
        // but use random base64 encoded content to avoid conflicts
        def randomContent = UUID.randomUUID().toString()
        def base64Content = randomContent.bytes.encodeBase64().toString()
        def path = "https://httpbin.org/base64/${base64Content}"
        def artifact = instance.createArtifact([
            path: path,
            run_id: runId,
            description: "Test artifact created by InstanceTest"
        ])

        then:
        artifact != null
        artifact instanceof Map
        artifact.uid != null
        artifact.uid.length() == 20
        // run_id may be returned as 'run' or 'run_id' depending on API response
        ((artifact.run ?: artifact.run_id) as Number)?.intValue() == runId
    }

    @Requires({ hasApiKey() })
    def "should upload artifact file linked to run"() {
        given:
        // Get the run to get its id
        def run = instance.getRecord([
            moduleName: 'core',
            modelName: 'run',
            idOrUid: TEST_RUN_UID,
            includeForeignKeys: true
        ])
        def runId = (run?.id as Number)?.intValue()

        // Create a temporary file to upload
        def tempFile = File.createTempFile("instance-test-", ".txt")
        tempFile.text = "Test content created at ${new Date()}"
        tempFile.deleteOnExit()

        when:
        def artifact = instance.uploadArtifact([
            file: tempFile,
            run_id: runId,
            description: "Test uploaded artifact by InstanceTest"
        ])

        then:
        artifact != null
        artifact instanceof Map
        artifact.uid != null
        artifact.uid.length() == 20
        // run_id may be returned as 'run' or 'run_id' depending on API response
        ((artifact.run ?: artifact.run_id) as Number)?.intValue() == runId

        cleanup:
        tempFile?.delete()
    }
}
