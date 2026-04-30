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

import java.nio.file.FileSystemNotFoundException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.ProviderMismatchException
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

/**
 * Tests for LaminS3FileSystemProvider.
 *
 * A TestableLaminS3FileSystemProvider subclass overrides createS3Client() to inject
 * a mock AwsS3Client, enabling unit tests without real AWS credentials.
 */
class LaminS3FileSystemProviderTest extends Specification {

    /**
     * Test subclass that overrides createS3Client() to return a mock AwsS3Client.
     */
    static class TestableLaminS3FileSystemProvider extends LaminS3FileSystemProvider {
        AwsS3Client injectedClient

        @Override
        protected AwsS3Client createS3Client(String accessKeyId, String secretAccessKey, String sessionToken) {
            return injectedClient
        }
    }

    TestableLaminS3FileSystemProvider provider
    AwsS3Client s3Client

    def setup() {
        s3Client = Mock(AwsS3Client)
        provider = new TestableLaminS3FileSystemProvider(injectedClient: s3Client)
    }

    // ==================== Scheme ====================

    def "getScheme() returns 'lamin-s3'"() {
        expect:
        provider.getScheme() == 'lamin-s3'
    }

    // ==================== getOrCreateFileSystem ====================

    def "getOrCreateFileSystem() creates and returns a new filesystem"() {
        when:
        LaminS3FileSystem fs = provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')

        then:
        fs != null
        fs.storageRoot == 's3://bucket/prefix'
        fs.bucketName == 'bucket'
        fs.accessKeyId == 'AKID'
        fs.s3Client == s3Client
    }

    def "getOrCreateFileSystem() returns cached filesystem for same accessKeyId"() {
        given:
        LaminS3FileSystem fs1 = provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')

        when:
        LaminS3FileSystem fs2 = provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret2', 'token2')

        then:
        fs2.is(fs1)
    }

    def "getOrCreateFileSystem() creates new filesystem when accessKeyId changes"() {
        given:
        LaminS3FileSystem fs1 = provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID1', 'secret', 'token')
        AwsS3Client s3Client2 = Mock(AwsS3Client)
        provider.injectedClient = s3Client2

        when:
        LaminS3FileSystem fs2 = provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID2', 'secret', 'token')

        then:
        !fs2.is(fs1)
        fs2.accessKeyId == 'AKID2'
        fs2.s3Client == s3Client2
    }

    def "getOrCreateFileSystem() creates separate filesystems for different storageRoots"() {
        when:
        LaminS3FileSystem fs1 = provider.getOrCreateFileSystem('s3://bucket/prefix1', 'AKID', 'secret', 'token')
        LaminS3FileSystem fs2 = provider.getOrCreateFileSystem('s3://bucket/prefix2', 'AKID', 'secret', 'token')

        then:
        !fs2.is(fs1)
        fs1.storageRoot == 's3://bucket/prefix1'
        fs2.storageRoot == 's3://bucket/prefix2'
    }

    // ==================== removeFileSystem ====================

    def "removeFileSystem() removes the filesystem from the cache"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        provider.removeFileSystem('s3://bucket/prefix')

        when:
        provider.getFileSystem(new URI('lamin-s3://bucket/key'))

        then:
        thrown(FileSystemNotFoundException)
    }

    // ==================== getFileSystem ====================

    def "getFileSystem(URI) returns the filesystem matching the bucket"() {
        given:
        LaminS3FileSystem expected = provider.getOrCreateFileSystem('s3://my-bucket/prefix', 'AKID', 'secret', 'token')

        when:
        def fs = provider.getFileSystem(new URI('lamin-s3://my-bucket/any/key'))

        then:
        fs.is(expected)
    }

    def "getFileSystem(URI) throws FileSystemNotFoundException for unknown bucket"() {
        when:
        provider.getFileSystem(new URI('lamin-s3://no-such-bucket/key'))

        then:
        thrown(FileSystemNotFoundException)
    }

    // ==================== getPath ====================

    def "getPath(URI) returns a LaminS3Path for a known bucket"() {
        given:
        provider.getOrCreateFileSystem('s3://my-bucket/prefix', 'AKID', 'secret', 'token')

        when:
        def path = provider.getPath(new URI('lamin-s3://my-bucket/some/object.txt'))

        then:
        path instanceof LaminS3Path
        (path as LaminS3Path).bucket == 'my-bucket'
        (path as LaminS3Path).key == 'some/object.txt'
    }

    def "getPath(URI) throws FileSystemNotFoundException for unknown bucket"() {
        when:
        provider.getPath(new URI('lamin-s3://no-such-bucket/key'))

        then:
        thrown(FileSystemNotFoundException)
    }

    // ==================== newFileSystem ====================

    def "newFileSystem(URI, env) delegates to getOrCreateFileSystem"() {
        given:
        Map<String, Object> env = [
            storageRoot    : 's3://bucket/prefix',
            accessKeyId    : 'AKID',
            secretAccessKey: 'secret',
            sessionToken   : 'token'
        ]

        when:
        def fs = provider.newFileSystem(new URI('lamin-s3://bucket/'), env)

        then:
        fs instanceof LaminS3FileSystem
        (fs as LaminS3FileSystem).storageRoot == 's3://bucket/prefix'
    }

    // ==================== Non-S3 file operations ====================

    def "isSameFile() returns true for equal paths"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def p1 = provider.getPath(new URI('lamin-s3://bucket/a/b'))
        def p2 = provider.getPath(new URI('lamin-s3://bucket/a/b'))

        expect:
        provider.isSameFile(p1, p2)
    }

    def "isSameFile() returns false for different paths"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def p1 = provider.getPath(new URI('lamin-s3://bucket/a/b'))
        def p2 = provider.getPath(new URI('lamin-s3://bucket/x/y'))

        expect:
        !provider.isSameFile(p1, p2)
    }

    def "isHidden() always returns false"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def p = provider.getPath(new URI('lamin-s3://bucket/hidden/.hidden'))

        expect:
        !provider.isHidden(p)
    }

    def "canUpload() always returns false"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def s3Path = provider.getPath(new URI('lamin-s3://bucket/a/b'))
        def localPath = java.nio.file.Paths.get('/tmp/local.txt')

        expect:
        !provider.canUpload(localPath, s3Path)
    }

    def "canDownload() returns true for S3 source and local target"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def s3Path = provider.getPath(new URI('lamin-s3://bucket/a/b'))
        Path localPath = Files.createTempDirectory('lamin-test').resolve('file.txt')

        expect:
        provider.canDownload(s3Path, localPath)
    }

    def "canDownload() returns false for local→local"() {
        given:
        def local1 = java.nio.file.Paths.get('/tmp/a.txt')
        def local2 = java.nio.file.Paths.get('/tmp/b.txt')

        expect:
        !provider.canDownload(local1, local2)
    }

    // ==================== Unsupported operations ====================

    def "newOutputStream() throws UnsupportedOperationException"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def p = provider.getPath(new URI('lamin-s3://bucket/k'))

        when:
        provider.newOutputStream(p)

        then:
        thrown(UnsupportedOperationException)
    }

    def "newByteChannel() throws UnsupportedOperationException"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def p = provider.getPath(new URI('lamin-s3://bucket/k'))

        when:
        provider.newByteChannel(p, Collections.emptySet())

        then:
        thrown(UnsupportedOperationException)
    }

    def "newDirectoryStream() throws UnsupportedOperationException"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def p = provider.getPath(new URI('lamin-s3://bucket/k'))

        when:
        provider.newDirectoryStream(p, { true })

        then:
        thrown(UnsupportedOperationException)
    }

    def "createDirectory() throws UnsupportedOperationException"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def p = provider.getPath(new URI('lamin-s3://bucket/k'))

        when:
        provider.createDirectory(p)

        then:
        thrown(UnsupportedOperationException)
    }

    def "delete() throws UnsupportedOperationException"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def p = provider.getPath(new URI('lamin-s3://bucket/k'))

        when:
        provider.delete(p)

        then:
        thrown(UnsupportedOperationException)
    }

    def "move() throws UnsupportedOperationException"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def p1 = provider.getPath(new URI('lamin-s3://bucket/a'))
        def p2 = provider.getPath(new URI('lamin-s3://bucket/b'))

        when:
        provider.move(p1, p2)

        then:
        thrown(UnsupportedOperationException)
    }

    def "getFileStore() throws UnsupportedOperationException"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def p = provider.getPath(new URI('lamin-s3://bucket/k'))

        when:
        provider.getFileStore(p)

        then:
        thrown(UnsupportedOperationException)
    }

    def "setAttribute() throws UnsupportedOperationException"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def p = provider.getPath(new URI('lamin-s3://bucket/k'))

        when:
        provider.setAttribute(p, 'custom:attr', 'value')

        then:
        thrown(UnsupportedOperationException)
    }

    def "upload() throws UnsupportedOperationException"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def s3Path = provider.getPath(new URI('lamin-s3://bucket/k'))
        def localPath = java.nio.file.Paths.get('/tmp/local.txt')

        when:
        provider.upload(localPath, s3Path)

        then:
        thrown(UnsupportedOperationException)
    }

    // ==================== Attribute views ====================

    def "getFileAttributeView() returns null"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def p = provider.getPath(new URI('lamin-s3://bucket/k'))

        expect:
        provider.getFileAttributeView(p, BasicFileAttributes) == null
    }

    def "readAttributes(path, String) returns empty map"() {
        given:
        provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        def p = provider.getPath(new URI('lamin-s3://bucket/k'))

        expect:
        provider.readAttributes(p, 'basic:*').isEmpty()
    }

    // ==================== S3 I/O operations (mocked) ====================

    private LaminS3Path s3Path(String key) {
        LaminS3FileSystem fs = provider.getOrCreateFileSystem('s3://bucket/prefix', 'AKID', 'secret', 'token')
        return new LaminS3Path(fs, key)
    }

    private static ResponseInputStream<GetObjectResponse> responseStream(byte[] content) {
        return new ResponseInputStream<>(
            GetObjectResponse.builder().build(),
            AbortableInputStream.create(new ByteArrayInputStream(content))
        )
    }

    def "newInputStream() returns an InputStream backed by S3 getObject"() {
        given:
        def p = s3Path('prefix/file.txt')
        byte[] content = 'hello s3'.bytes
        s3Client.getObject(_ as GetObjectRequest) >> responseStream(content)

        when:
        InputStream stream = provider.newInputStream(p)
        byte[] read = stream.bytes

        then:
        read == content
    }

    def "newInputStream() throws NoSuchFileException when key does not exist"() {
        given:
        def p = s3Path('prefix/missing.txt')
        s3Client.getObject(_ as GetObjectRequest) >> { throw NoSuchKeyException.builder().message('not found').statusCode(404).build() }

        when:
        provider.newInputStream(p)

        then:
        thrown(NoSuchFileException)
    }

    def "checkAccess() calls headObject and succeeds when key exists"() {
        given:
        def p = s3Path('prefix/exists.txt')
        s3Client.headObject(_ as HeadObjectRequest) >> HeadObjectResponse.builder().contentLength(42L).build()

        when:
        provider.checkAccess(p)

        then:
        1 * s3Client.headObject(_ as HeadObjectRequest)
    }

    def "checkAccess() throws NoSuchFileException when key does not exist"() {
        given:
        def p = s3Path('prefix/missing.txt')
        s3Client.headObject(_ as HeadObjectRequest) >> { throw NoSuchKeyException.builder().message('not found').statusCode(404).build() }

        when:
        provider.checkAccess(p)

        then:
        thrown(NoSuchFileException)
    }

    def "readAttributes(path, Class) returns LaminS3FileAttributes"() {
        given:
        def p = s3Path('prefix/file.txt')
        s3Client.headObject(_ as HeadObjectRequest) >> HeadObjectResponse.builder().contentLength(100L).build()

        when:
        def attrs = provider.readAttributes(p, BasicFileAttributes)

        then:
        attrs instanceof LaminS3FileAttributes
        (attrs as LaminS3FileAttributes).size() == 100L
    }

    def "readAttributes(path, Class) throws NoSuchFileException when key does not exist"() {
        given:
        def p = s3Path('prefix/missing.txt')
        s3Client.headObject(_ as HeadObjectRequest) >> { throw NoSuchKeyException.builder().message('not found').statusCode(404).build() }

        when:
        provider.readAttributes(p, BasicFileAttributes)

        then:
        thrown(NoSuchFileException)
    }

    def "copy() copies S3 content to a local file"() {
        given:
        def p = s3Path('prefix/file.txt')
        byte[] content = 'copied content'.bytes
        s3Client.getObject(_ as GetObjectRequest) >> responseStream(content)
        Path tmpDir = Files.createTempDirectory('lamin-copy-test')
        Path target = tmpDir.resolve('output.txt')

        when:
        provider.copy(p, target)

        then:
        Files.readAllBytes(target) == content

        cleanup:
        tmpDir.toFile().deleteDir()
    }

    def "copy() throws NoSuchFileException when source key does not exist"() {
        given:
        def p = s3Path('prefix/missing.txt')
        s3Client.getObject(_ as GetObjectRequest) >> { throw NoSuchKeyException.builder().message('not found').statusCode(404).build() }
        Path tmpDir = Files.createTempDirectory('lamin-copy-test')
        Path target = tmpDir.resolve('output.txt')

        when:
        provider.copy(p, target)

        then:
        thrown(NoSuchFileException)

        cleanup:
        tmpDir.toFile().deleteDir()
    }

    def "download() writes S3 content to a local file"() {
        given:
        def p = s3Path('prefix/file.txt')
        byte[] content = 'downloaded content'.bytes
        s3Client.getObject(_ as GetObjectRequest) >> responseStream(content)
        Path tmpDir = Files.createTempDirectory('lamin-download-test')
        Path dest = tmpDir.resolve('output.txt')

        when:
        provider.download(p, dest)

        then:
        Files.readAllBytes(dest) == content

        cleanup:
        tmpDir.toFile().deleteDir()
    }

    def "download() throws NoSuchFileException when key does not exist"() {
        given:
        def p = s3Path('prefix/missing.txt')
        s3Client.getObject(_ as GetObjectRequest) >> { throw NoSuchKeyException.builder().message('not found').statusCode(404).build() }
        Path tmpDir = Files.createTempDirectory('lamin-download-test')
        Path dest = tmpDir.resolve('output.txt')

        when:
        provider.download(p, dest)

        then:
        thrown(NoSuchFileException)

        cleanup:
        tmpDir.toFile().deleteDir()
    }

    def "download() with REPLACE_EXISTING replaces existing file"() {
        given:
        def p = s3Path('prefix/file.txt')
        byte[] newContent = 'new content'.bytes
        s3Client.getObject(_ as GetObjectRequest) >> responseStream(newContent)
        Path tmpDir = Files.createTempDirectory('lamin-download-test')
        Path dest = tmpDir.resolve('existing.txt')
        Files.write(dest, 'old content'.bytes)

        when:
        provider.download(p, dest, StandardCopyOption.REPLACE_EXISTING)

        then:
        Files.readAllBytes(dest) == newContent

        cleanup:
        tmpDir.toFile().deleteDir()
    }

    // ==================== Provider mismatch ====================

    def "newInputStream() with non-LaminS3Path throws ProviderMismatchException"() {
        given:
        def localPath = java.nio.file.Paths.get('/tmp/local.txt')

        when:
        provider.newInputStream(localPath)

        then:
        thrown(ProviderMismatchException)
    }
}
