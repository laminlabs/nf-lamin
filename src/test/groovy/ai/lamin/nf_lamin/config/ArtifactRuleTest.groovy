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
 * Test for ArtifactRule configuration
 */
class ArtifactRuleTest extends Specification {

    def "should create rule with minimal config"() {
        when:
        def rule = new ArtifactRule([pattern: '.*\\.fastq$'])

        then:
        rule.enabled == true
        rule.pattern == '.*\\.fastq$'
        rule.type == 'include'
        rule.direction == 'both'
        rule.order == 100
        rule.ulabelUids == []
        rule.projectUids == []
    }

    def "should create rule with full config"() {
        when:
        def rule = new ArtifactRule([
            enabled: false,
            pattern: '.*\\.bam$',
            type: 'exclude',
            direction: 'output',
            kind: 'dataset',
            ulabel_uids: ['uid1', 'uid2'],
            project_uids: ['proj1'],
            order: 5
        ])

        then:
        rule.enabled == false
        rule.pattern == '.*\\.bam$'
        rule.type == 'exclude'
        rule.direction == 'output'
        rule.kind == 'dataset'
        rule.ulabelUids == ['uid1', 'uid2']
        rule.projectUids == ['proj1']
        rule.order == 5
    }

    def "should handle single string for ulabel_uids"() {
        when:
        def rule = new ArtifactRule([
            pattern: '.*\\.txt$',
            ulabel_uids: 'single-uid'
        ])

        then:
        rule.ulabelUids == ['single-uid']
    }

    def "should handle single string for project_uids"() {
        when:
        def rule = new ArtifactRule([
            pattern: '.*\\.txt$',
            project_uids: 'single-proj'
        ])

        then:
        rule.projectUids == ['single-proj']
    }

    def "should throw error for missing pattern"() {
        when:
        new ArtifactRule([enabled: true])

        then:
        thrown(IllegalArgumentException)
    }

    def "should throw error for invalid type"() {
        when:
        new ArtifactRule([pattern: '.*\\.txt$', type: 'invalid'])

        then:
        thrown(IllegalArgumentException)
    }

    def "should throw error for invalid direction"() {
        when:
        new ArtifactRule([pattern: '.*\\.txt$', direction: 'invalid'])

        then:
        thrown(IllegalArgumentException)
    }

    def "should throw error for invalid regex pattern"() {
        when:
        new ArtifactRule([pattern: '(unclosed group'])

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    def "should match pattern '#pattern' against '#path' = #expected"() {
        given:
        def rule = new ArtifactRule([pattern: pattern])

        expect:
        rule.matches(path) == expected

        where:
        pattern          | path                       | expected
        '.*\\.fastq$'    | 'file.fastq'              | true
        '.*\\.fastq$'    | 'file.fastq.gz'           | false
        '.*\\.fastq\\.gz$' | 'file.fastq.gz'         | true
        '.*/output/.*'   | 's3://bucket/output/file' | true
        '.*/output/.*'   | 's3://bucket/input/file'  | false
    }

    def "should not match when disabled"() {
        given:
        def rule = new ArtifactRule([pattern: '.*\\.txt$', enabled: false])

        expect:
        !rule.matches('file.txt')
    }

    @Unroll
    def "should apply to direction '#artifactDirection' when rule direction is '#ruleDirection' = #expected"() {
        given:
        def rule = new ArtifactRule([pattern: '.*', direction: ruleDirection])

        expect:
        rule.appliesToDirection(artifactDirection) == expected

        where:
        ruleDirection | artifactDirection | expected
        'both'        | 'input'           | true
        'both'        | 'output'          | true
        'input'       | 'input'           | true
        'input'       | 'output'          | false
        'output'      | 'input'           | false
        'output'      | 'output'          | true
    }
}
