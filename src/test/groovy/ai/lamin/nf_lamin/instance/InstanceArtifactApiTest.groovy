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
package ai.lamin.nf_lamin.instance

import ai.lamin.lamin_api_client.ApiException
import ai.lamin.nf_lamin.LaminConfig
import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.hub.LaminHubConfigResolver
import ai.lamin.nf_lamin.model.RunStatus
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Shared

import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime

/**
 * Integration tests for the artifact create/upload API endpoints.
 *
 * Tests the following endpoints with various input combinations:
 *   - POST /instances/{instance_id}/artifacts/create  (remote paths: s3, gs, https)
 *   - POST /instances/{instance_id}/artifacts/upload   (local files)
 *   - GET  /instances/{instance_id}/artifacts/by-path   (verify artifacts)
 *   - POST /instances/{instance_id}/modules/{module}/{model}/{id_or_uid}  (getRecord)
 *
 * Each test case is run with and without a run_id to exercise both code paths.
 *
 * Uses small, known-to-exist public files in s3://lamindata and gs://di-temporary-public
 * so no LAMIN_TEST_BUCKET is needed.
 *
 * Requires:
 *   - LAMIN_API_KEY environment variable
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
class InstanceArtifactApiTest extends Specification {

    @Shared String apiKey = System.getenv('LAMIN_API_KEY')
    @Shared Instance instance
    @Shared String instanceId
    @Shared Integer testRunId

    // Small, publicly accessible files that already exist and are NOT LaminDB-managed.
    // These are NOT used elsewhere in the test suite for createArtifact calls.
    static final String TEST_S3_PATH = 's3://1000genomes/CHANGELOG'
    static final String TEST_GS_PATH = 'gs://di-temporary-public/scratch/temp-bgzip/run_20251015_120418/run.bgzip.state.yaml'

    // LaminDB-managed path for negative testing (should fail when manually creating artifacts)
    static final String LAMIN_MANAGED_S3_PATH = 's3://lamindata/.lamindb/s3rtK8wIzJNKvg5Q0000.txt'

    // Track created artifact UIDs for cleanup reference
    @Shared List<String> createdArtifactUids = []

    // Unique suffix per test run to avoid collisions
    @Shared String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8)

    def setupSpec() {
        if (apiKey) {
            def config = LaminConfig.parseConfig([
                instance: 'laminlabs/lamindata',
                api_key: apiKey,
            ])
            def resolvedConfig = LaminHubConfigResolver.resolve(config)
            def hub = new LaminHub(
                resolvedConfig.supabaseApiUrl as String,
                resolvedConfig.supabaseAnonKey as String,
                resolvedConfig.apiKey as String
            )
            def settings = hub.getInstanceSettings(
                config.instanceOwner,
                config.instanceName
            )
            instance = new Instance(hub, settings, 3, 1000)
            instanceId = settings.id().toString()

            // --- Get or create transform ---
            String transformKey = 'nf-lamin-artifact-api-test'
            Map transform = null

            // Search for existing transform with this key
            try {
                List<Map> existing = instance.findTransforms([and: [[key: [eq: transformKey]]]])
                println "findTransforms for key='${transformKey}': found ${existing?.size() ?: 0} result(s)"
                if (existing) {
                    transform = existing[0]
                    println "Reusing existing transform: id=${transform.id}, uid=${transform.uid}"
                }
            } catch (Exception e) {
                println "findTransforms failed (will try createTransform instead)"
                println "  filter: [key: '${transformKey}']"
                println "  error: ${e.getMessage()}"
            }

            // Create if not found
            if (!transform) {
                Map<String, Object> transformInput = [
                    key: transformKey,
                    kind: 'pipeline',
                    source_code: '// artifact API test stub',
                ]
                try {
                    transform = instance.createTransform(transformInput)
                    println "Created new transform: id=${transform?.id}, uid=${transform?.uid}"
                } catch (Exception e) {
                    println "createTransform FAILED"
                    println "  input: ${transformInput}"
                    println "  error: ${e.getMessage()}"
                    if (e instanceof ApiException) {
                        println "  responseBody: ${((ApiException) e).responseBody}"
                    }
                    throw e
                }
            }
            assert transform?.id : "Failed to get or create test transform"

            // --- Create run ---
            String nowIso = OffsetDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx"))
            Map<String, Object> runData = [
                transform_id: (transform.id as Number).intValue(),
                name: ("artifact-api-test-${uniqueSuffix}" as String),
                created_at: nowIso,
                started_at: nowIso,
                _status_code: (int) RunStatus.STARTED.code
            ] as Map<String, Object>
            Map run = null
            try {
                run = instance.createRun(runData)
                println "Created run: id=${run?.id}, uid=${run?.uid}"
            } catch (Exception e) {
                println "createRun FAILED"
                println "  input: ${runData}"
                println "  error: ${e.getMessage()}"
                if (e instanceof ApiException) {
                    println "  responseBody: ${((ApiException) e).responseBody}"
                }
                throw e
            }
            assert run?.id : "Failed to create test run. Response: ${run}"
            testRunId = run.id as Integer
        }
    }

    // -------------------------------------------------------------------
    // Helper: build a curl command string for reproducing a call
    // -------------------------------------------------------------------
    private static String curlForCreate(String instanceId, String path, Integer runId) {
        String body = runId != null
            ? """{"path":"${path}","run_id":${runId}}"""
            : """{"path":"${path}"}"""
        return """curl -s -X POST '${apiUrl()}/instances/${instanceId}/artifacts/create' \\
  -H 'Authorization: Bearer \$LAMIN_ACCESS_TOKEN' \\
  -H 'Content-Type: application/json' \\
  -d '${body}'"""
    }

    private static String curlForUpload(String instanceId, String fileName, Integer runId) {
        String kwargsFlag = runId != null
            ? """-F 'kwargs={"run_id":${runId}}'"""
            : ""
        return """curl -s -X POST '${apiUrl()}/instances/${instanceId}/artifacts/upload' \\
  -H 'Authorization: Bearer \$LAMIN_ACCESS_TOKEN' \\
  -F 'file=@${fileName}' ${kwargsFlag}"""
    }

    private static String curlForGetByPath(String instanceId, String path) {
        String encodedPath = URLEncoder.encode(path, 'UTF-8')
        return """curl -s -X GET '${apiUrl()}/instances/${instanceId}/artifacts/by-path?path=${encodedPath}' \\
  -H 'Authorization: Bearer \$LAMIN_ACCESS_TOKEN'"""
    }

    private static String curlForGetRecord(String instanceId, String module, String model, String idOrUid) {
        return """curl -s -X POST '${apiUrl()}/instances/${instanceId}/modules/${module}/${model}/${idOrUid}' \\
  -H 'Authorization: Bearer \$LAMIN_ACCESS_TOKEN' \\
  -H 'Content-Type: application/json' \\
  -d '{}'"""
    }

    private static String apiUrl() {
        return 'https://us.api.lamin.ai'
    }

    // -------------------------------------------------------------------
    // Helper: delete artifact at path if it already exists
    // -------------------------------------------------------------------
    private void deleteArtifactIfExists(String path) {
        try {
            Map<String, Object> existing = instance.getArtifactByPath(path)
            if (existing != null && existing.uid) {
                String uid = existing.uid as String
                println "Deleting pre-existing artifact at ${path}: uid=${uid}"
                instance.deleteRecord(
                    moduleName: 'core',
                    modelName: 'artifact',
                    uid: uid
                )
                println "Successfully deleted artifact ${uid}"
            }
        } catch (Exception e) {
            // Non-fatal: log and continue
            println "WARNING: Could not delete pre-existing artifact at ${path}: ${e.message}"
        }
    }

    // -------------------------------------------------------------------
    // Helper: assert that the artifact we just created can be fetched
    // back via getArtifactByPath and getRecord, and that fields match.
    // -------------------------------------------------------------------
    private void verifyArtifactRetrieval(Map<String, Object> artifact, String expectedPath, Integer runId) {
        String uid = artifact.uid as String
        assert uid : failMsg("Created artifact has no uid. Artifact response: ${artifact}", curlForGetRecord(instanceId, 'core', 'artifact', 'UNKNOWN'))

        createdArtifactUids << uid

        // --- getRecord (POST /modules/core/artifact/{uid}) ---
        Map<String, Object> record = null
        try {
            record = instance.getRecord(
                moduleName: 'core',
                modelName: 'artifact',
                idOrUid: uid,
                includeForeignKeys: true
            )
        } catch (Exception e) {
            assert false : failMsg(
                "getRecord for artifact uid=${uid} failed: ${exceptionDetail(e)}",
                curlForGetRecord(instanceId, 'core', 'artifact', uid),
                [moduleName: 'core', modelName: 'artifact', idOrUid: uid]
            )
        }
        assert record : failMsg(
            "getRecord returned null for artifact uid=${uid}",
            curlForGetRecord(instanceId, 'core', 'artifact', uid),
            [moduleName: 'core', modelName: 'artifact', idOrUid: uid]
        )
        assert record.uid?.toString()?.startsWith(uid.substring(0, 16)) : failMsg(
            "getRecord uid mismatch: expected prefix ${uid.substring(0, 16)}, got ${record.uid}. Full record: ${record}",
            curlForGetRecord(instanceId, 'core', 'artifact', uid),
            [moduleName: 'core', modelName: 'artifact', idOrUid: uid]
        )

        // Verify run linkage
        if (runId != null) {
            // The artifact should reference the run
            def artifactRun = record.run ?: record.run_id
            if (artifactRun instanceof Map) {
                assert artifactRun.id == runId : failMsg(
                    "Artifact run.id mismatch: expected ${runId}, got ${artifactRun.id}. Full record: ${record}",
                    curlForGetRecord(instanceId, 'core', 'artifact', uid),
                    [expectedRunId: runId, actualRecord: record]
                )
            } else if (artifactRun != null) {
                def actualRunId = (artifactRun as Number)?.intValue()
                // For createArtifact on existing paths, the artifact may already be linked to a different run
                // Only fail if the path was expected to create a new artifact (uploaded files)
                if (expectedPath == null || actualRunId == runId) {
                    assert actualRunId == runId : failMsg(
                        "Artifact run_id mismatch: expected ${runId}, got ${actualRunId}. Full record: ${record}",
                        curlForGetRecord(instanceId, 'core', 'artifact', uid),
                        [expectedRunId: runId, actualRecord: record]
                    )
                } else {
                    println "NOTE: Artifact at ${expectedPath} already exists with run_id=${actualRunId} (expected ${runId})"
                }
            } else if (expectedPath == null) {
                // For uploaded files (no expectedPath), run_id should always be set
                assert false : failMsg(
                    "Artifact uid=${uid} should reference run_id=${runId} but run/run_id is null. " +
                    "Record keys: ${record.keySet()}. Full record: ${record}",
                    curlForGetRecord(instanceId, 'core', 'artifact', uid),
                    [expectedRunId: runId, actualRecord: record]
                )
            }
        }

        // --- getArtifactByPath (GET /artifacts/by-path) ---
        if (expectedPath) {
            Map<String, Object> byPath = null
            try {
                byPath = instance.getArtifactByPath(expectedPath)
            } catch (Exception e) {
                assert false : failMsg(
                    "getArtifactByPath for path='${expectedPath}' failed: ${exceptionDetail(e)}",
                    curlForGetByPath(instanceId, expectedPath),
                    [path: expectedPath]
                )
            }
            // byPath may be null for uploaded files (their path is auto-generated),
            // but for remote createArtifact paths it should match.
            if (byPath != null) {
                assert byPath.uid?.toString()?.startsWith(uid.substring(0, 16)) : failMsg(
                    "getArtifactByPath uid mismatch: expected prefix ${uid.substring(0, 16)}, got ${byPath.uid}. Full response: ${byPath}",
                    curlForGetByPath(instanceId, expectedPath),
                    [path: expectedPath, expectedUidPrefix: uid.substring(0, 16)]
                )
            }
        }
    }

    private static String failMsg(String message, String curl, Map inputData = null) {
        StringBuilder sb = new StringBuilder(message)
        if (inputData != null) {
            sb.append("\n\nInput data:\n  ${inputData}")
        }
        sb.append("\n\nReproduce with:\n${curl}")
        return sb.toString()
    }

    private static String exceptionDetail(Exception e) {
        String detail = e.getMessage() ?: e.toString()
        if (e instanceof ApiException) {
            detail += "\n  Response body: ${((ApiException) e).responseBody}"
        }
        return detail
    }

    // ===================================================================
    //  createArtifact tests — remote paths
    // ===================================================================

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "createArtifact with S3 path, without run_id"() {
        given:
        String s3Path = TEST_S3_PATH
        deleteArtifactIfExists(s3Path)

        when:
        Map<String, Object> inputArgs = [path: s3Path]
        Map<String, Object> artifact = null
        try {
            artifact = instance.createArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "createArtifact (S3, no run_id) failed: ${exceptionDetail(e)}",
                curlForCreate(instanceId, s3Path, null),
                inputArgs
            )
        }

        then:
        artifact != null
        artifact.uid != null
        verifyArtifactRetrieval(artifact, s3Path, null)
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "createArtifact with S3 path, with run_id"() {
        given:
        String s3Path = TEST_S3_PATH
        deleteArtifactIfExists(s3Path)

        when:
        Map<String, Object> inputArgs = [path: s3Path, run_id: testRunId]
        Map<String, Object> artifact = null
        try {
            artifact = instance.createArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "createArtifact (S3, run_id=${testRunId}) failed: ${exceptionDetail(e)}",
                curlForCreate(instanceId, s3Path, testRunId),
                inputArgs
            )
        }

        then:
        artifact != null
        artifact.uid != null
        verifyArtifactRetrieval(artifact, s3Path, testRunId)
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "createArtifact with GS path, without run_id"() {
        given:
        String gsPath = TEST_GS_PATH
        deleteArtifactIfExists(gsPath)

        when:
        Map<String, Object> inputArgs = [path: gsPath]
        Map<String, Object> artifact = null
        try {
            artifact = instance.createArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "createArtifact (GS, no run_id) failed: ${exceptionDetail(e)}",
                curlForCreate(instanceId, gsPath, null),
                inputArgs
            )
        }

        then:
        artifact != null
        artifact.uid != null
        verifyArtifactRetrieval(artifact, gsPath, null)
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "createArtifact with GS path, with run_id"() {
        given:
        String gsPath = TEST_GS_PATH
        deleteArtifactIfExists(gsPath)

        when:
        Map<String, Object> inputArgs = [path: gsPath, run_id: testRunId]
        Map<String, Object> artifact = null
        try {
            artifact = instance.createArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "createArtifact (GS, run_id=${testRunId}) failed: ${exceptionDetail(e)}",
                curlForCreate(instanceId, gsPath, testRunId),
                inputArgs
            )
        }

        then:
        artifact != null
        artifact.uid != null
        verifyArtifactRetrieval(artifact, gsPath, testRunId)
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "createArtifact with HTTPS path, without run_id"() {
        given:
        String httpsPath = "https://raw.githubusercontent.com/laminlabs/nf-lamin/main/README.md"
        deleteArtifactIfExists(httpsPath)

        when:
        Map<String, Object> inputArgs = [path: httpsPath]
        Map<String, Object> artifact = null
        try {
            artifact = instance.createArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "createArtifact (HTTPS, no run_id) failed: ${exceptionDetail(e)}",
                curlForCreate(instanceId, httpsPath, null),
                inputArgs
            )
        }

        then:
        artifact != null
        artifact.uid != null
        verifyArtifactRetrieval(artifact, httpsPath, null)
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "createArtifact with HTTPS path, with run_id"() {
        given:
        String httpsPath = "https://raw.githubusercontent.com/laminlabs/nf-lamin/main/LICENSE"
        deleteArtifactIfExists(httpsPath)

        when:
        Map<String, Object> inputArgs = [path: httpsPath, run_id: testRunId]
        Map<String, Object> artifact = null
        try {
            artifact = instance.createArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "createArtifact (HTTPS, run_id=${testRunId}) failed: ${exceptionDetail(e)}",
                curlForCreate(instanceId, httpsPath, testRunId),
                inputArgs
            )
        }

        then:
        artifact != null
        artifact.uid != null
        verifyArtifactRetrieval(artifact, httpsPath, testRunId)
    }

    // ===================================================================
    //  uploadArtifact tests — local files
    // ===================================================================

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "uploadArtifact with local file, without run_id"() {
        given:
        Path tempFile = Files.createTempFile("nf-lamin-upload-no-run-${uniqueSuffix}", '.txt')
        Files.writeString(tempFile, "Upload test without run_id — ${uniqueSuffix} — ${System.nanoTime()}")

        when:
        Map<String, Object> inputArgs = [file: tempFile.toFile(), description: "Upload test ${uniqueSuffix}"]
        Map<String, Object> artifact = null
        try {
            artifact = instance.uploadArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "uploadArtifact (no run_id) failed: ${exceptionDetail(e)}",
                curlForUpload(instanceId, tempFile.toAbsolutePath().toString(), null),
                [file: tempFile.toAbsolutePath().toString(), description: inputArgs.description]
            )
        }

        then:
        artifact != null
        artifact.uid != null
        // For uploaded files the path is auto-generated, so we don't pass an expectedPath to by-path lookup
        verifyArtifactRetrieval(artifact, null, null)

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "uploadArtifact with local file, with run_id"() {
        given:
        Path tempFile = Files.createTempFile("nf-lamin-upload-with-run-${uniqueSuffix}", '.txt')
        Files.writeString(tempFile, "Upload test with run_id — ${uniqueSuffix} — ${System.nanoTime()}")

        when:
        Map<String, Object> inputArgs = [file: tempFile.toFile(), run_id: testRunId]
        Map<String, Object> artifact = null
        try {
            artifact = instance.uploadArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "uploadArtifact (run_id=${testRunId}) failed: ${exceptionDetail(e)}",
                curlForUpload(instanceId, tempFile.toAbsolutePath().toString(), testRunId),
                [file: tempFile.toAbsolutePath().toString(), run_id: testRunId]
            )
        }

        then:
        artifact != null
        artifact.uid != null
        verifyArtifactRetrieval(artifact, null, testRunId)

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "uploadArtifact with local binary file"() {
        given:
        Path tempFile = Files.createTempFile("nf-lamin-upload-binary-${uniqueSuffix}", '.bin')
        byte[] randomBytes = new byte[1024]
        new Random().nextBytes(randomBytes)
        Files.write(tempFile, randomBytes)

        when:
        Map<String, Object> inputArgs = [file: tempFile.toFile(), description: "Binary upload test ${uniqueSuffix}"]
        Map<String, Object> artifact = null
        try {
            artifact = instance.uploadArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "uploadArtifact (binary, no run_id) failed: ${exceptionDetail(e)}",
                curlForUpload(instanceId, tempFile.toAbsolutePath().toString(), null),
                [file: tempFile.toAbsolutePath().toString(), description: inputArgs.description]
            )
        }

        then:
        artifact != null
        artifact.uid != null
        verifyArtifactRetrieval(artifact, null, null)

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "uploadArtifact with local file and description kwarg"() {
        given:
        Path tempFile = Files.createTempFile("nf-lamin-upload-desc-${uniqueSuffix}", '.txt')
        Files.writeString(tempFile, "Upload test with description — ${uniqueSuffix}")

        when:
        Map<String, Object> inputArgs = [file: tempFile.toFile(), description: "Test artifact from InstanceArtifactApiTest ${uniqueSuffix}"]
        Map<String, Object> artifact = null
        try {
            artifact = instance.uploadArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "uploadArtifact (with description) failed: ${exceptionDetail(e)}",
                curlForUpload(instanceId, tempFile.toAbsolutePath().toString(), null),
                [file: tempFile.toAbsolutePath().toString(), description: inputArgs.description]
            )
        }

        then:
        artifact != null
        artifact.uid != null
        verifyArtifactRetrieval(artifact, null, null)

        // Verify description was set
        Map<String, Object> record = instance.getRecord(
            moduleName: 'core',
            modelName: 'artifact',
            idOrUid: artifact.uid as String
        )
        record.description?.toString()?.contains(uniqueSuffix)

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    // ===================================================================
    //  createArtifact with description kwarg
    // ===================================================================

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "createArtifact with S3 path and description kwarg"() {
        given:
        String s3Path = TEST_S3_PATH
        deleteArtifactIfExists(s3Path)

        when:
        Map<String, Object> inputArgs = [path: s3Path, description: "S3 artifact with description ${uniqueSuffix}"]
        Map<String, Object> artifact = null
        try {
            artifact = instance.createArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "createArtifact (S3, description) failed: ${exceptionDetail(e)}",
                curlForCreate(instanceId, s3Path, null),
                inputArgs
            )
        }

        then:
        artifact != null
        artifact.uid != null
        verifyArtifactRetrieval(artifact, s3Path, null)
    }

    // ===================================================================
    //  createArtifact duplicate / idempotency
    // ===================================================================

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "createArtifact twice with same S3 path returns same or new artifact"() {
        given:
        String s3Path = TEST_S3_PATH
        deleteArtifactIfExists(s3Path)

        when:
        Map<String, Object> artifact1 = null
        Map<String, Object> artifact2 = null
        Map<String, Object> inputArgs = [path: s3Path]
        try {
            artifact1 = instance.createArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "createArtifact (duplicate test, first call) failed: ${exceptionDetail(e)}",
                curlForCreate(instanceId, s3Path, null),
                inputArgs
            )
        }
        try {
            artifact2 = instance.createArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "createArtifact (duplicate test, second call) failed: ${exceptionDetail(e)}",
                curlForCreate(instanceId, s3Path, null),
                inputArgs
            )
        }

        then:
        artifact1 != null
        artifact2 != null
        // Both should succeed — whether they return the same UID or a new version
        artifact1.uid != null
        artifact2.uid != null
    }

    // ===================================================================
    //  Negative test: LaminDB-managed S3 path should fail
    // ===================================================================

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "createArtifact with LaminDB-managed S3 path should fail"() {
        given:
        String laminManagedPath = LAMIN_MANAGED_S3_PATH

        when:
        Map<String, Object> inputArgs = [path: laminManagedPath]
        Exception caughtException = null
        try {
            instance.createArtifact(inputArgs)
        } catch (Exception e) {
            caughtException = e
        }

        then:
        // Should fail because manually creating artifacts for LaminDB-managed paths is not allowed
        caughtException != null
        println "Expected failure for LaminDB-managed path: ${exceptionDetail(caughtException)}"
        println "Input: ${inputArgs}"
        println "Reproduce: ${curlForCreate(instanceId, laminManagedPath, null)}"
    }

    // ===================================================================
    //  getArtifactByPath for non-existent path
    // ===================================================================

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "getArtifactByPath returns null for non-existent path"() {
        when:
        Map<String, Object> result = instance.getArtifactByPath(
            "s3://non-existent-bucket-${uniqueSuffix}/does-not-exist.txt"
        )

        then:
        result == null
    }

    // ===================================================================
    //  getRecord for non-existent artifact returns null or throws
    // ===================================================================

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "getRecord for non-existent artifact UID returns null"() {
        when:
        Map<String, Object> result = instance.getRecord(
            moduleName: 'core',
            modelName: 'artifact',
            idOrUid: 'zzzzzzzzzzzzzzzz'
        )

        then:
        // API returns null for 404
        result == null
    }

    // ===================================================================
    //  uploadArtifact with empty file
    // ===================================================================

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "uploadArtifact with empty file"() {
        given:
        Path tempFile = Files.createTempFile("nf-lamin-upload-empty-${uniqueSuffix}", '.txt')
        // File is empty (0 bytes)

        when:
        Map<String, Object> artifact = null
        Exception caughtException = null
        try {
            artifact = instance.uploadArtifact([file: tempFile.toFile()])
        } catch (Exception e) {
            caughtException = e
        }

        then:
        // Either succeeds or fails gracefully — document the behavior
        if (caughtException) {
            println "NOTE: uploadArtifact with empty file failed (this may be expected): ${exceptionDetail(caughtException)}"
            println "Input: [file: ${tempFile.toAbsolutePath()}]"
            println "Reproduce: ${curlForUpload(instanceId, tempFile.toAbsolutePath().toString(), null)}"
        } else {
            assert artifact != null
            assert artifact.uid != null
        }

        cleanup:
        Files.deleteIfExists(tempFile)
    }

    // ===================================================================
    //  createArtifact with both run_id and description
    // ===================================================================

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "createArtifact with S3 path, run_id, and description"() {
        given:
        String s3Path = TEST_S3_PATH
        deleteArtifactIfExists(s3Path)

        when:
        Map<String, Object> inputArgs = [path: s3Path, run_id: testRunId, description: "Full params test ${uniqueSuffix}"]
        Map<String, Object> artifact = null
        try {
            artifact = instance.createArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "createArtifact (S3, run_id=${testRunId}, description) failed: ${exceptionDetail(e)}",
                curlForCreate(instanceId, s3Path, testRunId),
                inputArgs
            )
        }

        then:
        artifact != null
        artifact.uid != null
        verifyArtifactRetrieval(artifact, s3Path, testRunId)

        // Verify description via getRecord
        Map<String, Object> record = instance.getRecord(
            moduleName: 'core',
            modelName: 'artifact',
            idOrUid: artifact.uid as String
        )
        record.description?.toString()?.contains(uniqueSuffix)
    }

    // ===================================================================
    //  uploadArtifact with both run_id and description
    // ===================================================================

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "uploadArtifact with local file, run_id, and description"() {
        given:
        Path tempFile = Files.createTempFile("nf-lamin-upload-full-${uniqueSuffix}", '.csv')
        Files.writeString(tempFile, "col1,col2\n1,2\n3,4\n${uniqueSuffix},${System.nanoTime()}\n")

        when:
        Map<String, Object> inputArgs = [file: tempFile.toFile(), run_id: testRunId, description: "CSV upload test ${uniqueSuffix}"]
        Map<String, Object> artifact = null
        try {
            artifact = instance.uploadArtifact(inputArgs)
        } catch (Exception e) {
            assert false : failMsg(
                "uploadArtifact (run_id + description) failed: ${exceptionDetail(e)}",
                curlForUpload(instanceId, tempFile.toAbsolutePath().toString(), testRunId),
                [file: tempFile.toAbsolutePath().toString(), run_id: testRunId, description: inputArgs.description]
            )
        }

        then:
        artifact != null
        artifact.uid != null
        verifyArtifactRetrieval(artifact, null, testRunId)

        // Check suffix is csv
        Map<String, Object> record = instance.getRecord(
            moduleName: 'core',
            modelName: 'artifact',
            idOrUid: artifact.uid as String
        )
        if (record.suffix) {
            assert record.suffix.toString().replaceAll(/^\./, '') == 'csv' : failMsg(
                "Expected suffix 'csv' but got '${record.suffix}'. Full record: ${record}",
                curlForGetRecord(instanceId, 'core', 'artifact', artifact.uid as String),
                [expectedSuffix: 'csv', actualSuffix: record.suffix]
            )
        }

        cleanup:
        Files.deleteIfExists(tempFile)
    }
}
