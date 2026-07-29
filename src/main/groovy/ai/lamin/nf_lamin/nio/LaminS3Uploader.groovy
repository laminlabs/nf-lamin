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

package ai.lamin.nf_lamin.nio

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.file.Files
import java.nio.file.Path

import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload
import software.amazon.awssdk.services.s3.model.CompletedPart
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.UploadPartRequest

/**
 * Uploads local files to S3 using LaminHub session credentials.
 *
 * Objects above {@link #DEFAULT_THRESHOLD} are uploaded as a multipart upload. Beyond
 * keeping large objects within the 5 GB single-PUT limit, that also fails fast: the
 * URL-connection HTTP client the plugin ships with does not send {@code Expect: 100-continue},
 * so a rejected single PUT is only discovered after the whole body has been sent.
 */
@Slf4j
@CompileStatic
class LaminS3Uploader {

    /** Objects at or below this size are uploaded with a single PutObject. */
    static final long DEFAULT_THRESHOLD = 100L * 1024 * 1024

    /** Size of each part of a multipart upload. */
    static final long DEFAULT_PART_SIZE = 100L * 1024 * 1024

    /** Smallest part size S3 accepts for anything but the last part. */
    static final long MIN_PART_SIZE = 5L * 1024 * 1024

    /** Most parts S3 accepts in a single multipart upload. */
    static final int MAX_PARTS = 10_000

    private final AwsS3Client client
    private final long threshold
    private final long partSize

    LaminS3Uploader(AwsS3Client client, long threshold = DEFAULT_THRESHOLD, long partSize = DEFAULT_PART_SIZE) {
        this.client = client
        this.threshold = threshold
        this.partSize = Math.max(partSize, MIN_PART_SIZE)
    }

    /**
     * Upload a local file to {@code bucket/key}, overwriting whatever is there.
     *
     * @throws IOException if the upload fails; a multipart upload is aborted first, so no
     *         parts are left behind to be billed for
     */
    void upload(Path localFile, String bucket, String key) throws IOException {
        long size = Files.size(localFile)
        if (size <= threshold) {
            putObject(localFile, bucket, key, size)
        }
        else {
            multipartUpload(localFile, bucket, key, size)
        }
    }

    private void putObject(Path localFile, String bucket, String key, long size) throws IOException {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentLength(size)
                .build()
            client.putObject(request, RequestBody.fromFile(localFile))
            log.trace "Uploaded ${localFile} to s3://${bucket}/${key} (${size} bytes)"
        }
        catch (Exception e) {
            throw new IOException("Failed to upload ${localFile} to s3://${bucket}/${key}", e)
        }
    }

    private void multipartUpload(Path localFile, String bucket, String key, long size) throws IOException {
        // Grow the parts rather than exceed the part limit, so objects beyond
        // partSize * MAX_PARTS still go up in one upload
        long effectivePartSize = Math.max(partSize, ceilDiv(size, MAX_PARTS))
        int partCount = (int) ceilDiv(size, effectivePartSize)

        String uploadId = null
        try {
            CreateMultipartUploadResponse created = client.createMultipartUpload(
                CreateMultipartUploadRequest.builder().bucket(bucket).key(key).build()
            )
            uploadId = created.uploadId()
            log.debug "Started multipart upload of ${localFile} to s3://${bucket}/${key} in ${partCount} part(s)"

            List<CompletedPart> parts = []
            for (int partNumber = 1; partNumber <= partCount; partNumber++) {
                long offset = (partNumber - 1) * effectivePartSize
                long length = Math.min(effectivePartSize, size - offset)

                UploadPartRequest partRequest = UploadPartRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .contentLength(length)
                    .build()

                // A content provider can be re-read, so the SDK's own retries work
                String eTag = client.uploadPart(
                    partRequest,
                    RequestBody.fromContentProvider({ partStream(localFile, offset, length) }, length, 'application/octet-stream')
                ).eTag()

                parts.add(CompletedPart.builder().partNumber(partNumber).eTag(eTag).build())
            }

            client.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                    .build()
            )
            log.trace "Completed multipart upload of ${localFile} to s3://${bucket}/${key} (${size} bytes)"
        }
        catch (Exception e) {
            abortQuietly(bucket, key, uploadId)
            throw new IOException("Failed to upload ${localFile} to s3://${bucket}/${key}", e)
        }
    }

    private void abortQuietly(String bucket, String key, String uploadId) {
        if (uploadId == null) {
            return
        }
        try {
            client.abortMultipartUpload(
                AbortMultipartUploadRequest.builder().bucket(bucket).key(key).uploadId(uploadId).build()
            )
        }
        catch (Exception e) {
            log.warn "Could not abort multipart upload ${uploadId} of s3://${bucket}/${key}; " +
                "it may keep incurring storage costs until the bucket lifecycle rule cleans it up: ${e.message}"
        }
    }

    private static InputStream partStream(Path localFile, long offset, long length) {
        InputStream stream = Files.newInputStream(localFile)
        stream.skip(offset)
        return new BoundedInputStream(stream, length)
    }

    private static long ceilDiv(long value, long divisor) {
        return (value + divisor - 1) / divisor as long
    }

    /**
     * Reads at most {@code limit} bytes from a stream, so a single part cannot run over
     * into the next one.
     */
    @CompileStatic
    private static class BoundedInputStream extends FilterInputStream {

        private long remaining

        BoundedInputStream(InputStream delegate, long limit) {
            super(delegate)
            this.remaining = limit
        }

        @Override
        int read() throws IOException {
            if (remaining <= 0) {
                return -1
            }
            int value = super.read()
            if (value >= 0) {
                remaining--
            }
            return value
        }

        @Override
        int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1
            }
            int read = super.read(buffer, offset, (int) Math.min(length as long, remaining))
            if (read > 0) {
                remaining -= read
            }
            return read
        }

        @Override
        int available() throws IOException {
            return (int) Math.min(super.available() as long, remaining)
        }
    }
}
