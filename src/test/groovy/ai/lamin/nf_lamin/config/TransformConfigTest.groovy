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
        config.ulabelUids == []
    }

    def "should create config with empty map"() {
        when:
        def config = new TransformConfig([:])

        then:
        config.ulabelUids == []
    }

    def "should create config with null map"() {
        when:
        def config = new TransformConfig(null)

        then:
        config.ulabelUids == []
    }

    def "should parse single ulabel UID as string"() {
        when:
        def config = new TransformConfig([ulabel_uids: 'ulab123456789012'])

        then:
        config.ulabelUids == ['ulab123456789012']
    }

    def "should parse multiple ulabel UIDs as list"() {
        when:
        def config = new TransformConfig([ulabel_uids: ['ulab111111111111', 'ulab222222222222']])

        then:
        config.ulabelUids == ['ulab111111111111', 'ulab222222222222']
    }

    def "should parse multiple ulabel UIDs"() {
        when:
        def config = new TransformConfig([
            ulabel_uids: ['ulab111111111111', 'ulab222222222222']
        ])

        then:
        config.ulabelUids == ['ulab111111111111', 'ulab222222222222']
    }

    @Unroll
    def "should filter out null/empty values from list: #input"() {
        when:
        def config = new TransformConfig([ulabel_uids: input])

        then:
        config.ulabelUids == expected

        where:
        input                                  | expected
        ['ulab111', null, 'ulab222']           | ['ulab111', 'ulab222']
        ['ulab111', '', 'ulab222']             | ['ulab111', 'ulab222']
        [null, null]                           | []
        ['', '']                               | []
    }

    def "should handle mixed string/number types"() {
        when:
        def config = new TransformConfig([
            ulabel_uids: [456, 'ulab111']
        ])

        then:
        config.ulabelUids == ['456', 'ulab111']
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
            ulabel_uids: true     // boolean instead of string/list
        ])

        then:
        config.ulabelUids == []
    }

    def "should provide getter methods"() {
        given:
        def config = new TransformConfig([
            ulabel_uids: ['ulab111']
        ])

        expect:
        config.getUlabelUids() == ['ulab111']
    }

    def "should include ulabelUids in toString"() {
        when:
        def config = new TransformConfig([
            ulabel_uids: ['ulab111']
        ])
        def str = config.toString()

        then:
        str.contains('ulabelUids=[ulab111]')
    }
}
