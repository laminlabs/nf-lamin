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

package ai.lamin.nf_lamin.config

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Test for TransformConfig
 */
class TransformConfigTest extends Specification {

    def "should create empty config with default constructor"() {
        when:
        def config = new TransformConfig()

        then:
        config.projectUids == []
        config.ulabelUids == []
    }

    def "should create config with empty map"() {
        when:
        def config = new TransformConfig([:])

        then:
        config.projectUids == []
        config.ulabelUids == []
    }

    def "should create config with null map"() {
        when:
        def config = new TransformConfig(null)

        then:
        config.projectUids == []
        config.ulabelUids == []
    }

    def "should parse single project UID as string"() {
        when:
        def config = new TransformConfig([project_uids: 'proj123456789012'])

        then:
        config.projectUids == ['proj123456789012']
        config.ulabelUids == []
    }

    def "should parse multiple project UIDs as list"() {
        when:
        def config = new TransformConfig([project_uids: ['proj111111111111', 'proj222222222222']])

        then:
        config.projectUids == ['proj111111111111', 'proj222222222222']
        config.ulabelUids == []
    }

    def "should parse single ulabel UID as string"() {
        when:
        def config = new TransformConfig([ulabel_uids: 'ulab123456789012'])

        then:
        config.projectUids == []
        config.ulabelUids == ['ulab123456789012']
    }

    def "should parse multiple ulabel UIDs as list"() {
        when:
        def config = new TransformConfig([ulabel_uids: ['ulab111111111111', 'ulab222222222222']])

        then:
        config.projectUids == []
        config.ulabelUids == ['ulab111111111111', 'ulab222222222222']
    }

    def "should parse both project and ulabel UIDs"() {
        when:
        def config = new TransformConfig([
            project_uids: ['proj111111111111', 'proj222222222222'],
            ulabel_uids: ['ulab111111111111', 'ulab222222222222']
        ])

        then:
        config.projectUids == ['proj111111111111', 'proj222222222222']
        config.ulabelUids == ['ulab111111111111', 'ulab222222222222']
    }

    @Unroll
    def "should filter out null/empty values from list: #input"() {
        when:
        def config = new TransformConfig([project_uids: input])

        then:
        config.projectUids == expected

        where:
        input                                  | expected
        ['proj111', null, 'proj222']           | ['proj111', 'proj222']
        ['proj111', '', 'proj222']             | ['proj111', 'proj222']
        [null, null]                           | []
        ['', '']                               | []
    }

    def "should handle mixed string/number types"() {
        when:
        def config = new TransformConfig([
            project_uids: ['proj111', 123, 'proj222'],
            ulabel_uids: [456, 'ulab111']
        ])

        then:
        config.projectUids == ['proj111', '123', 'proj222']
        config.ulabelUids == ['456', 'ulab111']
    }

    def "should return empty list when project_uids is null"() {
        when:
        def config = new TransformConfig([project_uids: null])

        then:
        config.projectUids == []
    }

    def "should return empty list when ulabel_uids is null"() {
        when:
        def config = new TransformConfig([ulabel_uids: null])

        then:
        config.ulabelUids == []
    }

    def "should handle non-standard input types gracefully"() {
        when:
        def config = new TransformConfig([
            project_uids: 12345,  // number instead of string/list
            ulabel_uids: true     // boolean instead of string/list
        ])

        then:
        config.projectUids == []
        config.ulabelUids == []
    }

    def "should provide getter methods"() {
        given:
        def config = new TransformConfig([
            project_uids: ['proj111'],
            ulabel_uids: ['ulab111']
        ])

        expect:
        config.getProjectUids() == ['proj111']
        config.getUlabelUids() == ['ulab111']
    }
}
