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

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import software.amazon.awssdk.services.s3.model.UploadPartResponse

class LaminS3UploaderTest extends Specification {

    @TempDir
    Path tempDir

    AwsS3Client client

    def setup() {
        client = Mock(AwsS3Client)
    }

    private Path file(int bytes) {
        Path path = tempDir.resolve("payload-${bytes}.bin")
        Files.write(path, new byte[bytes])
        return path
    }

    def "should upload a small file with a single PutObject"() {
        given:
        def uploader = new LaminS3Uploader(client)

        when:
        uploader.upload(file(1024), 'my-bucket', 'results/small.bin')

        then:
        1 * client.putObject({ PutObjectRequest r ->
            r.bucket() == 'my-bucket' && r.key() == 'results/small.bin' && r.contentLength() == 1024L
        }, _ as RequestBody)
        0 * client.createMultipartUpload(_)
    }

    def "should upload a large file as an ordered multipart upload"() {
        given: 'a threshold and part size small enough to split a tiny file'
        def uploader = new LaminS3Uploader(client, 100L, LaminS3Uploader.MIN_PART_SIZE)
        def partNumbers = []

        when:
        uploader.upload(file((int) (LaminS3Uploader.MIN_PART_SIZE * 2 + 10)), 'my-bucket', 'results/big.bin')

        then:
        1 * client.createMultipartUpload({ CreateMultipartUploadRequest r ->
            r.bucket() == 'my-bucket' && r.key() == 'results/big.bin'
        }) >> CreateMultipartUploadResponse.builder().uploadId('upload-1').build()

        then: 'three parts, in order'
        3 * client.uploadPart({ UploadPartRequest r ->
            partNumbers << r.partNumber()
            r.uploadId() == 'upload-1'
        }, _ as RequestBody) >> { UploadPartRequest r, RequestBody body ->
            UploadPartResponse.builder().eTag("etag-${r.partNumber()}").build()
        }

        then:
        1 * client.completeMultipartUpload({ CompleteMultipartUploadRequest r ->
            r.uploadId() == 'upload-1' &&
            r.multipartUpload().parts()*.partNumber() == [1, 2, 3] &&
            r.multipartUpload().parts()*.eTag() == ['etag-1', 'etag-2', 'etag-3']
        })

        and:
        partNumbers == [1, 2, 3]
        0 * client.abortMultipartUpload(_)
    }

    def "should abort a multipart upload that fails partway"() {
        given:
        def uploader = new LaminS3Uploader(client, 100L, LaminS3Uploader.MIN_PART_SIZE)

        and:
        client.createMultipartUpload(_) >> CreateMultipartUploadResponse.builder().uploadId('upload-1').build()
        client.uploadPart(_, _ as RequestBody) >> { throw S3Exception.builder().message('boom').build() }

        when:
        uploader.upload(file((int) (LaminS3Uploader.MIN_PART_SIZE * 2)), 'my-bucket', 'results/big.bin')

        then:
        1 * client.abortMultipartUpload({ AbortMultipartUploadRequest r -> r.uploadId() == 'upload-1' })
        0 * client.completeMultipartUpload(_)

        and:
        thrown(IOException)
    }

    def "should wrap a failed PutObject in an IOException"() {
        given:
        def uploader = new LaminS3Uploader(client)
        client.putObject(_ as PutObjectRequest, _ as RequestBody) >> { throw S3Exception.builder().message('denied').build() }

        when:
        uploader.upload(file(10), 'my-bucket', 'results/small.bin')

        then:
        def e = thrown(IOException)
        e.message.contains('s3://my-bucket/results/small.bin')
    }

    def "should grow the part size rather than exceed the part limit"() {
        given: 'a part size that would need more than MAX_PARTS parts'
        def uploader = new LaminS3Uploader(client, 100L, LaminS3Uploader.MIN_PART_SIZE)
        def size = LaminS3Uploader.MIN_PART_SIZE * LaminS3Uploader.MAX_PARTS + 1

        and:
        client.createMultipartUpload(_) >> CreateMultipartUploadResponse.builder().uploadId('upload-1').build()
        client.uploadPart(_, _ as RequestBody) >> { UploadPartRequest r, RequestBody body ->
            UploadPartResponse.builder().eTag('etag').build()
        }

        when: 'a sparse file of that size is uploaded'
        Path sparse = tempDir.resolve('sparse.bin')
        def channel = new RandomAccessFile(sparse.toFile(), 'rw')
        channel.setLength(size)
        channel.close()
        uploader.upload(sparse, 'my-bucket', 'results/huge.bin')

        then:
        1 * client.completeMultipartUpload({ CompleteMultipartUploadRequest r ->
            r.multipartUpload().parts().size() <= LaminS3Uploader.MAX_PARTS
        })
    }
}
