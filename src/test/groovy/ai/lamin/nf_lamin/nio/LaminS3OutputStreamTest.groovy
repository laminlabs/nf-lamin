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

import java.nio.file.Files
import java.nio.file.Path

class LaminS3OutputStreamTest extends Specification {

    LaminS3Uploader uploader

    def setup() {
        uploader = Mock(LaminS3Uploader)
    }

    def "should upload the buffered content once on close"() {
        given:
        def out = new LaminS3OutputStream(uploader, 'my-bucket', 'results/report.txt')
        Path uploaded = null

        when:
        out.write('hello '.bytes)
        out.write('world'.bytes)
        out.close()
        out.close()

        then: 'closing twice uploads once'
        1 * uploader.upload(_ as Path, 'my-bucket', 'results/report.txt') >> { Path p, String b, String k ->
            uploaded = p
            assert Files.readAllBytes(p) == 'hello world'.bytes
        }
    }

    def "should remove the temporary file even when the upload fails"() {
        given:
        def out = new LaminS3OutputStream(uploader, 'my-bucket', 'results/report.txt')
        Path tempFile = null
        uploader.upload(_ as Path, _, _) >> { Path p, String b, String k ->
            tempFile = p
            throw new IOException('boom')
        }

        when:
        out.write('hello'.bytes)
        out.close()

        then:
        thrown(IOException)
        tempFile != null
        !Files.exists(tempFile)
    }
}
