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

import spock.lang.Specification
import java.nio.file.Path

import ai.lamin.nf_lamin.nio.LaminS3FileSystem
import ai.lamin.nf_lamin.nio.LaminS3FileSystemProvider
import ai.lamin.nf_lamin.nio.LaminS3Path
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client

class PathUtilsTest extends Specification {

    // ========== toPaths tests ==========

    def 'toPaths returns a single path as is'() {
        given:
        Path path = Path.of('/tmp/output.json')

        expect:
        PathUtils.toPaths(path, 'myFunction') == [path]
    }

    def 'toPaths resolves a path string'() {
        expect:
        PathUtils.toPaths('/tmp/output.json', 'myFunction') == [Path.of('/tmp/output.json')]
    }

    def 'toPaths resolves a GString'() {
        given:
        def name = 'output'

        expect:
        PathUtils.toPaths("/tmp/${name}.json", 'myFunction') == [Path.of('/tmp/output.json')]
    }

    def 'toPaths flattens a nested collection'() {
        expect:
        PathUtils.toPaths([Path.of('/tmp/a.json'), ['/tmp/b.json']], 'myFunction') ==
            [Path.of('/tmp/a.json'), Path.of('/tmp/b.json')]
    }

    def 'toPaths rejects null'() {
        when:
        PathUtils.toPaths(null, 'myFunction')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('myFunction')
        e.message.contains('null')
    }

    def 'toPaths rejects an unsupported type'() {
        when:
        PathUtils.toPaths(42, 'myFunction')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('myFunction')
        e.message.contains('cannot resolve a Integer to a file')
    }

    // ========== toUriKey tests ==========

    def 'toUriKey returns null for a null path'() {
        expect:
        PathUtils.toUriKey(null) == null
    }

    def 'toUriKey absolutises a relative path'() {
        given:
        Path relative = Path.of('output.json')

        expect:
        PathUtils.toUriKey(relative) == relative.toAbsolutePath().toUri().toString()
    }

    def 'toUriKey keeps the leading slashes of a local path'() {
        expect:
        PathUtils.toUriKey(Path.of('/tmp/output.json')) == 'file:///tmp/output.json'
    }

    def 'toUriKey normalises a path with . and .. segments'() {
        expect:
        PathUtils.toUriKey(Path.of('/tmp/sub/../output.json')) == 'file:///tmp/output.json'
    }

    def 'toUriKey keeps the plugin-only lamin-s3 scheme'() {
        expect:
        PathUtils.toUriKey(laminS3Path('prefix/file.txt')) == 'lamin-s3://my-bucket/prefix/file.txt'
    }

    // ========== toStorageUri tests ==========

    def 'toStorageUri returns null for a null path'() {
        expect:
        PathUtils.toStorageUri(null) == null
    }

    def 'toStorageUri renders a lamin-s3 path with the s3 scheme'() {
        expect:
        PathUtils.toStorageUri(laminS3Path('9fm7UN13/.lamindb/abc0000.yaml')) ==
            's3://my-bucket/9fm7UN13/.lamindb/abc0000.yaml'
    }

    def 'toStorageUri leaves an ordinary path to toUriKey'() {
        given:
        Path path = Path.of('/tmp/output.json')

        expect:
        PathUtils.toStorageUri(path) == PathUtils.toUriKey(path)
    }

    private LaminS3Path laminS3Path(String key) {
        def fs = new LaminS3FileSystem(
            Mock(LaminS3FileSystemProvider), 's3://my-bucket/prefix', Mock(AwsS3Client), 'key1'
        )
        return new LaminS3Path(fs, key)
    }
}
