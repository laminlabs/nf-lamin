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

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/**
 * Helper for S3 operations in integration tests.
 *
 * Credential resolution (in priority order):
 *   1. LAMIN_TEST_AWS_ACCESS_KEY_ID / LAMIN_TEST_AWS_SECRET_ACCESS_KEY
 *      — use these locally to avoid clobbering the user's default AWS profile.
 *   2. AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY
 *      — standard SDK env vars; the values exported in GitHub CI.
 *
 * Usage:
 * <pre>
 *   // Check availability before creating
 *   if (!S3TestHelper.available()) return
 *
 *   S3TestHelper s3 = new S3TestHelper()
 *   String s3Path = s3.upload('my-bucket', 'prefix/file.txt', 'hello world'.bytes)
 *   // ... run test ...
 *   s3.delete('my-bucket', 'prefix/file.txt')
 *   s3.close()
 * </pre>
 *
 * Or use the convenience method that parses LAMIN_TEST_BUCKET automatically:
 * <pre>
 *   S3TestHelper s3 = new S3TestHelper()
 *   String s3Path = s3.uploadToTestBucket('nf-lamin-test/myfile.txt', content)
 *   s3.deleteFromTestBucket('nf-lamin-test/myfile.txt')
 *   s3.close()
 * </pre>
 */
class S3TestHelper implements Closeable {

    private final S3Client s3Client

    /** Bucket parsed from LAMIN_TEST_BUCKET (without s3:// prefix). */
    final String testBucket

    /** Key prefix parsed from LAMIN_TEST_BUCKET (may be empty string). */
    final String testKeyPrefix

    S3TestHelper() {
        String accessKeyId = System.getenv('LAMIN_TEST_AWS_ACCESS_KEY_ID') ?: System.getenv('AWS_ACCESS_KEY_ID')
        String secretKey   = System.getenv('LAMIN_TEST_AWS_SECRET_ACCESS_KEY') ?: System.getenv('AWS_SECRET_ACCESS_KEY')

        s3Client = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretKey)))
            .region(Region.US_EAST_1)
            .build()

        String rawBucket = System.getenv('LAMIN_TEST_BUCKET') ?: ''
        String stripped = rawBucket.replaceFirst(/^s3:\/\//, '')
        int slashIdx = stripped.indexOf('/')
        testBucket    = slashIdx >= 0 ? stripped.substring(0, slashIdx) : stripped
        testKeyPrefix = slashIdx >= 0 ? stripped.substring(slashIdx + 1).replaceAll(/\/$/, '') + '/' : ''
    }

    /**
     * Returns true when all required env vars are present so the helper can be used.
     * Call this in {@code @IgnoreIf} conditions or test setup guards.
     */
    static boolean available() {
        String bucket = System.getenv('LAMIN_TEST_BUCKET') ?: ''
        boolean hasS3Bucket = bucket.startsWith('s3://')
        boolean hasCreds    = System.getenv('LAMIN_TEST_AWS_ACCESS_KEY_ID') ||
                              System.getenv('AWS_ACCESS_KEY_ID')
        return hasS3Bucket && hasCreds
    }

    /**
     * Uploads {@code content} bytes to {@code bucket}/{@code key} and returns
     * the full {@code s3://bucket/key} URI.
     */
    String upload(String bucket, String key, byte[] content) {
        s3Client.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).build(),
            RequestBody.fromBytes(content)
        )
        return "s3://${bucket}/${key}"
    }

    /**
     * Uploads {@code content} to {@code testBucket}/{@code testKeyPrefix + relativeKey}
     * and returns the full {@code s3://...} URI.
     */
    String uploadToTestBucket(String relativeKey, byte[] content) {
        String key = testKeyPrefix + relativeKey
        return upload(testBucket, key, content)
    }

    /** Deletes {@code bucket}/{@code key}. Logs a warning on failure rather than throwing. */
    void delete(String bucket, String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
        } catch (Exception e) {
            println "WARNING: could not delete s3://${bucket}/${key}: ${e.message}"
        }
    }

    /**
     * Deletes {@code testBucket}/{@code testKeyPrefix + relativeKey}.
     * Logs a warning on failure rather than throwing.
     */
    void deleteFromTestBucket(String relativeKey) {
        delete(testBucket, testKeyPrefix + relativeKey)
    }

    @Override
    void close() {
        s3Client?.close()
    }
}
