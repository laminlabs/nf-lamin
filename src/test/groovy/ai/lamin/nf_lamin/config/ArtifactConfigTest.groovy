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
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Test for ArtifactConfig configuration
 */
class ArtifactConfigTest extends Specification {

    def "should create config with minimal settings"() {
        when:
        def config = new ArtifactConfig([:], 'both')

        then:
        config.enabled == true
        config.direction == 'both'
        config.includePattern == null
        config.excludePattern == null
        config.ulabelUids == []
        config.rules.isEmpty()
    }

    def "should create config with global patterns"() {
        when:
        def config = new ArtifactConfig([
            enabled: true,
            include_pattern: '.*\\.(fastq|bam)$',
            exclude_pattern: '.*temp.*',
            ulabel_uids: ['global-label'],
            kind: 'dataset'
        ], 'output')

        then:
        config.enabled == true
        config.direction == 'output'
        config.includePattern == '.*\\.(fastq|bam)$'
        config.excludePattern == '.*temp.*'
        config.ulabelUids == ['global-label']
        config.kind == 'dataset'
    }

    def "should handle single string for ulabel_uids"() {
        when:
        def config = new ArtifactConfig([
            ulabel_uids: 'single-label'
        ], 'both')

        then:
        config.ulabelUids == ['single-label']
    }

    def "should parse rules from config"() {
        when:
        def config = new ArtifactConfig([
            rules: [
                fastqs: [
                    pattern: '.*\\.fastq\\.gz$',
                    ulabel_uids: ['fastq-label']
                ],
                bams: [
                    enabled: false,
                    pattern: '.*\\.bam$'
                ]
            ]
        ], 'output')

        then:
        config.rules.size() == 2
        config.rules.containsKey('fastqs')
        config.rules.containsKey('bams')
        config.rules.fastqs.pattern == '.*\\.fastq\\.gz$'
        config.rules.fastqs.direction == 'output'
        config.rules.bams.enabled == false
    }

    def "should throw error for invalid include pattern"() {
        when:
        new ArtifactConfig([include_pattern: '(unclosed'], 'both')

        then:
        thrown(IllegalArgumentException)
    }

    def "should throw error for invalid exclude pattern"() {
        when:
        new ArtifactConfig([exclude_pattern: '(unclosed'], 'both')

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    def "evaluate applies patterns and returns metadata: #scenario"() {
        given:
        def config = new ArtifactConfig([
            enabled: enabled,
            include_pattern: includePattern,
            exclude_pattern: excludePattern,
            ulabel_uids: ['global-label'],
            kind: 'global-kind'
        ], direction)

        when:
        def result = config.evaluate(Paths.get("/test/${fileName}" as String), artifactDirection)

        then:
        result.shouldTrack == expectedShouldTrack
        // When tracked, should include global values
        if (expectedShouldTrack) {
            result.ulabelUids.contains('global-label')
            result.kind == 'global-kind'
        }

        where:
        scenario                          | enabled | includePattern      | excludePattern | direction | fileName           | artifactDirection | expectedShouldTrack
        'enabled with no patterns'        | true    | null                | null           | 'both'    | 'file.txt'         | 'output'          | true
        'disabled'                        | false   | null                | null           | 'both'    | 'file.txt'         | 'output'          | false
        'excluded by pattern'             | true    | null                | '.*temp.*'     | 'both'    | 'file_temp.txt'    | 'output'          | false
        'not excluded'                    | true    | null                | '.*temp.*'     | 'both'    | 'file.txt'         | 'output'          | true
        'included by pattern'             | true    | '.*\\.fastq$'       | null           | 'both'    | 'file.fastq'       | 'output'          | true
        'not included'                    | true    | '.*\\.fastq$'       | null           | 'both'    | 'file.txt'         | 'output'          | false
        'direction mismatch'              | true    | null                | null           | 'input'   | 'file.txt'         | 'output'          | false
        'direction match'                 | true    | null                | null           | 'input'   | 'file.txt'         | 'input'           | true
        'excluded takes precedence'       | true    | '.*\\.txt$'         | '.*temp.*'     | 'both'    | 'file_temp.txt'    | 'output'          | false
    }

    def "evaluate returns empty metadata when not tracked due to patterns"() {
        given:
        def config = new ArtifactConfig([
            enabled: true,
            exclude_pattern: '.*temp.*',
            ulabel_uids: ['global-label'],
            kind: 'global-kind'
        ], 'both')

        when:
        def result = config.evaluate(Paths.get('/test/file_temp.txt'), 'output')

        then:
        !result.shouldTrack
        result.ulabelUids == []
        result.kind == null
    }

    def "should determine tracking based on rules"() {
        given:
        def config = new ArtifactConfig([
            rules: [
                exclude_temp: [
                    pattern: '.*temp.*',
                    type: 'exclude',
                    order: 1
                ],
                include_fastq: [
                    pattern: '.*\\.fastq$',
                    type: 'include',
                    order: 2
                ]
            ]
        ], 'both')

        expect:
        !config.evaluate(Paths.get('/test/file_temp.txt'), 'output').shouldTrack  // excluded by rule
        config.evaluate(Paths.get('/test/file.fastq'), 'output').shouldTrack      // included by rule
        config.evaluate(Paths.get('/test/file.bam'), 'output').shouldTrack        // no matching rule, default to track
    }

    def "should get metadata from global config"() {
        given:
        def config = new ArtifactConfig([
            ulabel_uids: ['global-label'],
            kind: 'dataset'
        ], 'both')

        when:
        def result = config.evaluate(Paths.get('/test/file.txt'), 'output')

        then:
        result.ulabelUids == ['global-label']
        result.kind == 'dataset'
    }

    def "should merge metadata from rules"() {
        given:
        def config = new ArtifactConfig([
            ulabel_uids: ['global-label'],
            kind: 'dataset',
            rules: [
                fastqs: [
                    pattern: '.*\\.fastq$',
                    ulabel_uids: ['fastq-label'],
                    kind: 'raw_data'
                ]
            ]
        ], 'both')

        when:
        def result = config.evaluate(Paths.get('/test/file.fastq'), 'output')

        then:
        result.ulabelUids.containsAll(['global-label', 'fastq-label'])
        result.kind == 'raw_data'  // rule overrides global
    }

    def "should not duplicate metadata values"() {
        given:
        def config = new ArtifactConfig([
            ulabel_uids: ['label1', 'label2'],
            rules: [
                rule1: [
                    pattern: '.*\\.txt$',
                    ulabel_uids: ['label2', 'label3']
                ]
            ]
        ], 'both')

        when:
        def result = config.evaluate(Paths.get('/test/file.txt'), 'output')

        then:
        result.ulabelUids.size() == 3
        result.ulabelUids.containsAll(['label1', 'label2', 'label3'])
    }

    def "should accumulate metadata from multiple matching rules"() {
        given:
        def config = new ArtifactConfig([
            ulabel_uids: ['global-label'],
            kind: 'global-kind',
            rules: [
                all_files: [
                    pattern: '.*',
                    ulabel_uids: ['all-files-label'],
                    order: 10
                ],
                fastqs: [
                    pattern: '.*\\.fastq$',
                    ulabel_uids: ['fastq-label'],
                    kind: 'fastq-kind',
                    order: 1
                ]
            ]
        ], 'both')

        when:
        def result = config.evaluate(Paths.get('/test/file.fastq'), 'output')

        then:
        // Labels accumulated from global + both matching rules
        result.ulabelUids.containsAll(['global-label', 'fastq-label', 'all-files-label'])
        // Kind from last matching rule with kind (all_files has order=10, processed last)
        result.kind == 'fastq-kind'  // actually fastq has order=1, so all_files comes later but doesn't have kind
    }

    def "should use later rule's kind when multiple rules match"() {
        given:
        def config = new ArtifactConfig([
            kind: 'global-kind',
            rules: [
                first_rule: [
                    pattern: '.*',
                    kind: 'first-kind',
                    order: 1
                ],
                second_rule: [
                    pattern: '.*\\.txt$',
                    kind: 'second-kind',
                    order: 2
                ]
            ]
        ], 'both')

        when:
        def result = config.evaluate(Paths.get('/test/file.txt'), 'output')

        then:
        // second_rule comes after first_rule in order, so its kind takes precedence
        result.kind == 'second-kind'
    }

    def "should use last matching rule's type for tracking decision"() {
        given:
        def config = new ArtifactConfig([
            rules: [
                include_all: [
                    pattern: '.*',
                    type: 'include',
                    order: 1
                ],
                exclude_temp: [
                    pattern: '.*temp.*',
                    type: 'exclude',
                    order: 2
                ]
            ]
        ], 'both')

        expect:
        // Both rules match, last matching rule (exclude_temp) determines type
        !config.evaluate(Paths.get('/test/file_temp.txt'), 'output').shouldTrack
        // Only include_all matches
        config.evaluate(Paths.get('/test/file.txt'), 'output').shouldTrack
    }

    def "should allow later include rule to override earlier exclude rule"() {
        given:
        def config = new ArtifactConfig([
            rules: [
                exclude_all: [
                    pattern: '.*',
                    type: 'exclude',
                    order: 1
                ],
                include_fastq: [
                    pattern: '.*\\.fastq$',
                    type: 'include',
                    order: 2,
                    ulabel_uids: ['fastq-label']
                ]
            ]
        ], 'both')

        expect:
        // exclude_all matches but include_fastq (later) overrides it
        config.evaluate(Paths.get('/test/file.fastq'), 'output').shouldTrack
        config.evaluate(Paths.get('/test/file.fastq'), 'output').ulabelUids == ['fastq-label']
        // Only exclude_all matches, so excluded
        !config.evaluate(Paths.get('/test/file.txt'), 'output').shouldTrack
    }

    def "evaluate should resolve key from global template"() {
        given:
        def config = new ArtifactConfig([
            key: '{parent}/{basename}'
        ], 'both')

        when:
        def result = config.evaluate(Paths.get('/path/to/output/report.html'), 'output')

        then:
        result.shouldTrack
        result.key == 'output/report.html'
    }

    def "evaluate should resolve key from rule template"() {
        given:
        def config = new ArtifactConfig([
            rules: [
                structured_output: [
                    pattern: '.*/results/.*',
                    key: 'nf-core/rnaseq/{parent}/{basename}'
                ]
            ]
        ], 'both')

        when:
        def result = config.evaluate(Paths.get('/home/user/results/multiqc/report.html'), 'output')

        then:
        result.shouldTrack
        result.key == 'nf-core/rnaseq/multiqc/report.html'
    }

    def "evaluate should have null key when no key template is specified"() {
        given:
        def config = new ArtifactConfig([:], 'both')

        when:
        def result = config.evaluate(Paths.get('/path/to/file.txt'), 'output')

        then:
        result.shouldTrack
        result.key == null
    }

    def "evaluate rule key should override global key"() {
        given:
        def config = new ArtifactConfig([
            key: '{basename}',
            rules: [
                override_key: [
                    pattern: '.*/results/.*',
                    key: 'custom/{parent}/{basename}'
                ]
            ]
        ], 'both')

        when:
        def matchResult = config.evaluate(Paths.get('/home/results/star/Aligned.bam'), 'output')
        def noMatchResult = config.evaluate(Paths.get('/home/other/file.txt'), 'output')

        then:
        matchResult.key == 'custom/star/Aligned.bam'
        noMatchResult.key == 'file.txt'
    }

    def "evaluate should resolve key from global closure"() {
        given:
        Closure keyClosure = { Path p -> "closure-prefix/${p.fileName}" }
        def config = new ArtifactConfig([
            key: keyClosure
        ], 'both')

        when:
        def pathObj = Paths.get('/path/to/output/report.html')
        def result = config.evaluate(pathObj, 'output')

        then:
        result.shouldTrack
        result.key == 'closure-prefix/report.html'
    }

    def "evaluate should resolve key from rule closure"() {
        given:
        Closure keyClosure = { Path p -> "rule-output/${p.fileName}" }
        def config = new ArtifactConfig([
            rules: [
                with_closure: [
                    pattern: '.*\\.html$',
                    key: keyClosure
                ]
            ]
        ], 'both')

        when:
        def pathObj = Paths.get('/path/to/report.html')
        def result = config.evaluate(pathObj, 'output')

        then:
        result.shouldTrack
        result.key == 'rule-output/report.html'
    }

    def "evaluate rule closure should override global string key"() {
        given:
        Closure ruleClosure = { Path p -> "from-closure/${p.fileName}" }
        def config = new ArtifactConfig([
            key: '{parent}/{basename}',
            rules: [
                override_with_closure: [
                    pattern: '.*\\.bam$',
                    key: ruleClosure
                ]
            ]
        ], 'both')

        when:
        def matchPath = Paths.get('/results/star/Aligned.bam')
        def matchResult = config.evaluate(matchPath, 'output')
        def noMatchResult = config.evaluate(Paths.get('/results/star/Aligned.txt'), 'output')

        then:
        matchResult.key == 'from-closure/Aligned.bam'
        noMatchResult.key == 'star/Aligned.txt'
    }
}
