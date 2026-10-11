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

package ai.lamin.nf_lamin.util

import java.nio.file.Files
import java.nio.file.Path

import ai.lamin.nf_lamin.helpers.TestHelper
import ai.lamin.nf_lamin.model.ArtifactStats
import ai.lamin.nf_lamin.nio.LaminS3FileSystem
import ai.lamin.nf_lamin.nio.LaminS3FileSystemProvider
import ai.lamin.nf_lamin.nio.LaminS3Path
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.S3Object
import spock.lang.Specification

class ArtifactStatsResolverTest extends Specification {

    static final String HEX_A = md5Hex('a')
    static final String HEX_B = md5Hex('b')
    static final String HEX_ABC = md5Hex('abc')

    /** Resolver whose default client is a mock, so no credentials are touched. */
    static class TestableResolver extends ArtifactStatsResolver {
        AwsS3Client injectedClient
        int clientCreations = 0

        @Override
        protected AwsS3Client createDefaultClient() {
            clientCreations++
            return injectedClient
        }
    }

    AwsS3Client s3Client
    TestableResolver resolver

    def setup() {
        s3Client = Mock(AwsS3Client)
        resolver = new TestableResolver(injectedClient: s3Client)
    }

    // ========== single objects ==========

    def 'resolveS3 heads the object and converts its etag'() {
        when:
        ArtifactStats stats = resolver.resolveS3('b', 'dir/abc.txt')

        then:
        1 * s3Client.headObject({ HeadObjectRequest r -> r.bucket() == 'b' && r.key() == 'dir/abc.txt' }) >>
            HeadObjectResponse.builder().contentLength(3L).eTag('"' + HEX_ABC + '"').build()
        0 * s3Client.listObjectsV2(_)
        stats == new ArtifactStats(3L, 'kAFQmDzST7DWlj99KOF_cg', 'md5', null)
    }

    def 'resolveS3 keeps the part count of a multipart upload'() {
        given:
        s3Client.headObject(_ as HeadObjectRequest) >>
            HeadObjectResponse.builder().contentLength(300L).eTag('"' + HEX_ABC + '-3"').build()

        expect:
        resolver.resolveS3('b', 'big.bin') == new ArtifactStats(300L, 'kAFQmDzST7DWlj99KOF_cg', 'md5-3', null)
    }

    // ========== prefixes ==========

    def 'resolveS3 lists the prefix when the key is not an object'() {
        when:
        ArtifactStats stats = resolver.resolveS3('b', 'dir')

        then:
        1 * s3Client.headObject(_ as HeadObjectRequest) >> { throw notFound() }
        1 * s3Client.listObjectsV2({ ListObjectsV2Request r -> r.bucket() == 'b' && r.prefix() == 'dir/' && r.continuationToken() == null }) >>
            ListObjectsV2Response.builder().contents(obj('dir/a', 1L, HEX_A), obj('dir/sub/b', 1L, HEX_B + '-2')).isTruncated(false).build()
        stats == new ArtifactStats(2L, 'YNCwyFw7rBegqZLBVEckCg', 'md5-d', 2L)
    }

    def 'resolveS3 treats a generic 404 like a missing key'() {
        given:
        s3Client.headObject(_ as HeadObjectRequest) >> { throw S3Exception.builder().statusCode(404).message('Not Found').build() }
        s3Client.listObjectsV2(_ as ListObjectsV2Request) >>
            ListObjectsV2Response.builder().contents(obj('dir/a', 1L, HEX_A)).isTruncated(false).build()

        expect:
        resolver.resolveS3('b', 'dir').hashType == 'md5-d'
    }

    def 'resolveS3 follows continuation tokens'() {
        given:
        List<ListObjectsV2Request> requests = []
        s3Client.headObject(_ as HeadObjectRequest) >> { throw notFound() }
        s3Client.listObjectsV2(_ as ListObjectsV2Request) >> { ListObjectsV2Request r ->
            requests << r
            r.continuationToken() == null
                ? ListObjectsV2Response.builder().contents(obj('dir/a', 1L, HEX_A)).isTruncated(true).nextContinuationToken('tok').build()
                : ListObjectsV2Response.builder().contents(obj('dir/b', 1L, HEX_B)).isTruncated(false).build()
        }

        when:
        ArtifactStats stats = resolver.resolveS3('b', 'dir/')

        then:
        requests*.continuationToken() == [null, 'tok']
        requests*.prefix() == ['dir/', 'dir/']
        stats == new ArtifactStats(2L, 'YNCwyFw7rBegqZLBVEckCg', 'md5-d', 2L)
    }

    def 'resolveS3 strips a trailing slash before heading the key'() {
        when:
        resolver.resolveS3('b', 'dir/')

        then:
        1 * s3Client.headObject({ HeadObjectRequest r -> r.key() == 'dir' }) >> { throw notFound() }
        1 * s3Client.listObjectsV2(_ as ListObjectsV2Request) >> ListObjectsV2Response.builder().isTruncated(false).build()
    }

    def 'resolveS3 returns null for an empty prefix'() {
        given:
        s3Client.headObject(_ as HeadObjectRequest) >> { throw notFound() }
        s3Client.listObjectsV2(_ as ListObjectsV2Request) >> ListObjectsV2Response.builder().isTruncated(false).build()

        expect:
        resolver.resolveS3('b', 'missing') == null
    }

    def 'resolveS3 never hashes a whole bucket'() {
        when:
        ArtifactStats stats = resolver.resolveS3('b', key)

        then:
        stats == null
        0 * s3Client._

        where:
        key << ['', '/']
    }

    // ========== failures ==========

    def 'resolveS3 returns null when access is denied'() {
        when:
        ArtifactStats stats = resolver.resolveS3('b', 'secret.txt')

        then:
        1 * s3Client.headObject(_ as HeadObjectRequest) >> { throw S3Exception.builder().statusCode(403).message('Forbidden').build() }
        0 * s3Client.listObjectsV2(_)
        stats == null
    }

    def 'resolveS3 returns null when listing fails'() {
        given:
        s3Client.headObject(_ as HeadObjectRequest) >> { throw notFound() }
        s3Client.listObjectsV2(_ as ListObjectsV2Request) >> { throw new RuntimeException('boom') }

        expect:
        resolver.resolveS3('b', 'dir') == null
    }

    // ========== resolve(Path) ==========

    def 'resolve parses bucket and key from an s3 path'() {
        given:
        Path path = s3Path('s3://b/dir/a%20b.txt')

        when:
        ArtifactStats stats = resolver.resolve(path, false)

        then:
        1 * s3Client.headObject({ HeadObjectRequest r -> r.bucket() == 'b' && r.key() == 'dir/a b.txt' }) >>
            HeadObjectResponse.builder().contentLength(1L).eTag('"' + HEX_A + '"').build()
        stats.hash == 'DMF1ucDxtqgxw5niaXcmYQ'
    }

    def 'resolve uses the federated client of a lamin-s3 path'() {
        given:
        AwsS3Client federated = Mock(AwsS3Client)
        LaminS3FileSystem fs = new LaminS3FileSystem(Mock(LaminS3FileSystemProvider), 's3://my-bucket/prefix', federated, 'key1')
        Path path = new LaminS3Path(fs, 'prefix/file.txt')

        when:
        ArtifactStats stats = resolver.resolve(path, false)

        then:
        1 * federated.headObject({ HeadObjectRequest r -> r.bucket() == 'my-bucket' && r.key() == 'prefix/file.txt' }) >>
            HeadObjectResponse.builder().contentLength(1L).eTag('"' + HEX_A + '"').build()
        0 * s3Client._
        resolver.clientCreations == 0
        stats.hash == 'DMF1ucDxtqgxw5niaXcmYQ'
    }

    def 'resolve caches input stats but not output stats'() {
        given:
        Path path = s3Path('s3://b/in.txt')
        s3Client.headObject(_ as HeadObjectRequest) >> HeadObjectResponse.builder().contentLength(1L).eTag('"' + HEX_A + '"').build()

        when:
        resolver.resolve(path, true)
        resolver.resolve(path, true)
        resolver.resolve(path, false)

        then:
        2 * s3Client.headObject(_ as HeadObjectRequest)
    }

    def 'resolve caches a miss as well'() {
        given:
        Path path = s3Path('s3://b/denied.txt')
        s3Client.headObject(_ as HeadObjectRequest) >> { throw S3Exception.builder().statusCode(403).message('Forbidden').build() }

        when:
        ArtifactStats first = resolver.resolve(path, true)
        ArtifactStats second = resolver.resolve(path, true)

        then:
        1 * s3Client.headObject(_ as HeadObjectRequest)
        first == null
        second == null
    }

    def 'resolve leaves other schemes to the server'() {
        when:
        ArtifactStats stats = resolver.resolve(s3Path(uri), false)

        then:
        stats == null
        0 * s3Client._
        resolver.clientCreations == 0

        where:
        uri << ['gs://b/x.txt', 'https://example.com/x.txt', 'az://c/x.txt']
    }

    def 'resolve leaves in-memory filesystems to the server'() {
        expect:
        resolver.resolve(TestHelper.createInMemTempFile('x.txt', 'abc'), false) == null
    }

    def 'resolve hashes a local file and directory like lamindb'() {
        given:
        Path dir = Files.createTempDirectory('stats')
        Path a = dir.resolve('a')
        a.text = 'a'
        dir.resolve('b').text = 'b'

        expect:
        resolver.resolve(a, false) == new ArtifactStats(1L, 'DMF1ucDxtqgxw5niaXcmYQ', 'md5', null)
        resolver.resolve(dir, false) == new ArtifactStats(2L, 'YNCwyFw7rBegqZLBVEckCg', 'md5-d', 2L)

        cleanup:
        dir.deleteDir()
    }

    // ========== credentials ==========

    def 'buildCredentials prefers explicit keys from the nextflow aws scope'() {
        expect:
        ArtifactStatsResolver.buildCredentials([accessKey: 'AK', secretKey: 'SK'], Mock(AwsCredentialsProvider)) instanceof StaticCredentialsProvider
        ArtifactStatsResolver.buildCredentials([profile: 'lam'], Mock(AwsCredentialsProvider)) instanceof ProfileCredentialsProvider
    }

    def 'buildCredentials falls back to the default chain and then to anonymous access'() {
        given:
        AwsCredentialsProvider working = Mock(AwsCredentialsProvider)
        AwsCredentialsProvider broken = Mock(AwsCredentialsProvider) {
            resolveCredentials() >> { throw SdkClientException.create('Unable to load credentials') }
        }

        expect:
        ArtifactStatsResolver.buildCredentials([:], working).is(working)
        ArtifactStatsResolver.buildCredentials(null, broken) instanceof AnonymousCredentialsProvider
    }

    // ========== helpers ==========

    private Path s3Path(String uri) {
        Path path = Mock(Path)
        path.toUri() >> URI.create(uri)
        return path
    }

    private static NoSuchKeyException notFound() {
        return NoSuchKeyException.builder().statusCode(404).message('not found').build()
    }

    private static S3Object obj(String key, long size, String etagHex) {
        return S3Object.builder().key(key).size(size).eTag('"' + etagHex + '"').build()
    }

    private static String md5Hex(String content) {
        return java.security.MessageDigest.getInstance('MD5').digest(content.bytes).encodeHex().toString()
    }
}
