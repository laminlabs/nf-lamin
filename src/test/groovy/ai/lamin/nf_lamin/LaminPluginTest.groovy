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

import java.nio.file.Paths

import nextflow.file.FileHelper
import org.pf4j.PluginWrapper
import software.amazon.awssdk.services.s3.S3Client
import spock.lang.Specification

import ai.lamin.nf_lamin.nio.LaminS3FileSystem
import ai.lamin.nf_lamin.nio.LaminS3FileSystemProvider
import ai.lamin.nf_lamin.nio.LaminS3Path

/**
 * Unit tests for the LaminPlugin class
 */
class LaminPluginTest extends Specification {

    def "should create LaminPlugin with wrapper"() {
        given:
        def wrapper = Mock(PluginWrapper)

        when:
        def plugin = new LaminPlugin(wrapper)

        then:
        plugin != null
    }

    def "turnOffGlobbing() keeps Nextflow from reading the query string of a lamin:// target as a glob"() {
        given:
        def fs = new LaminS3FileSystem(new LaminS3FileSystemProvider(), 's3://my-bucket/prefix', Stub(S3Client))
        def target = new LaminS3Path(fs, 'prefix/results')

        when:
        LaminPlugin.turnOffGlobbing(LaminS3FileSystemProvider.SCHEME)

        then:
        !FileHelper.isGlobAllowed(target)
        FileHelper.isGlobAllowed(Paths.get('/tmp/results'))
    }

    def "turnOffGlobbing() is idempotent"() {
        when:
        LaminPlugin.turnOffGlobbing('lamin-s3')
        LaminPlugin.turnOffGlobbing('lamin-s3')

        then:
        noExceptionThrown()
    }
}
