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

import java.nio.file.Path

import software.amazon.awssdk.services.s3.S3Client as AwsS3Client

class LaminPathsTest extends Specification {

    def "toStorageUriString() renders a LaminS3Path as its real s3:// location"() {
        given:
        def fs = new LaminS3FileSystem(Mock(LaminS3FileSystemProvider), 's3://my-bucket/prefix', Mock(AwsS3Client), 'key1')

        expect:
        LaminPaths.toStorageUriString(new LaminS3Path(fs, 'prefix/sub/file.txt')) == 's3://my-bucket/prefix/sub/file.txt'
    }

    def "toStorageUriString() normalises the protocol separator"() {
        given:
        def path = Stub(Path) {
            toUri() >> new URI(uri)
        }

        expect:
        LaminPaths.toStorageUriString(path) == expected

        where:
        uri                             || expected
        's3://bucket/key.txt'           || 's3://bucket/key.txt'
        's3:///bucket/key.txt'          || 's3://bucket/key.txt'
        's3:/bucket/key.txt'            || 's3://bucket/key.txt'
        'gs://bucket/key.txt'           || 'gs://bucket/key.txt'
    }
}
