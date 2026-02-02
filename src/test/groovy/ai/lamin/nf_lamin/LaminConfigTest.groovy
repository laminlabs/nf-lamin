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

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Test for LaminConfig - focuses on configuration parsing only
 */
class LaminConfigTest extends Specification {

    def "should create valid config with required parameters"() {
        given:
        def config = [
            instance: 'owner/repo',
            api_key: 'test-key'
        ]

        when:
        def laminConfig = new LaminConfig(config)

        then:
        laminConfig.instance == 'owner/repo'
        laminConfig.instanceOwner == 'owner'
        laminConfig.instanceName == 'repo'
        laminConfig.apiKey == 'test-key'
        laminConfig.env == 'prod'  // default
        laminConfig.maxRetries == 3  // default
        laminConfig.retryDelay == 100  // default
    }

    def "should use provided values over defaults"() {
        when:
        def laminConfig = new LaminConfig([
            instance: 'provided/instance',
            api_key: 'provided-key',
            project: 'provided-project',
            env: 'staging'
        ])

        then:
        laminConfig.instance == 'provided/instance'
        laminConfig.apiKey == 'provided-key'
        laminConfig.project == 'provided-project'
        laminConfig.env == 'staging'

        cleanup:
        System.clearProperty('LAMIN_CURRENT_INSTANCE')
        System.clearProperty('LAMIN_API_KEY')
        System.clearProperty('LAMIN_CURRENT_PROJECT')
        System.clearProperty('LAMIN_ENV')
    }

    @Unroll
    def "should throw IllegalArgumentException for invalid instance '#instance'"() {
        when:
        new LaminConfig([instance: instance, api_key: 'test-key'])

        then:
        thrown(IllegalArgumentException)

        where:
        instance << [null, '', ' ', 'invalid-instance', 'owner/repo/extra']
    }

    @Unroll
    def "should throw IllegalArgumentException for invalid api key when not in environment"() {
        when:
        new LaminConfig([instance: 'owner/repo', api_key: apiKey])

        then:
        thrown(IllegalArgumentException)

        where:
        apiKey << [null, '', ' ']
    }

    def "should correctly parse instance owner and name"() {
        when:
        def config = new LaminConfig([instance: 'laminlabs/lamindata', api_key: 'test-key'])

        then:
        config.instanceOwner == 'laminlabs'
        config.instanceName == 'lamindata'
    }

    @Unroll
    def "should accept valid instance format '#validInstance'"() {
        when:
        def config = new LaminConfig([instance: validInstance, api_key: 'test-key'])

        then:
        config.instance == validInstance

        where:
        validInstance << [
            'owner/repo',
            'test-owner/test-repo',
            'laminlabs/lamindata',
            'user123/project456',
            'owner.with.dots/repo.with.dots',
            'owner-with-dashes/repo-with-dashes',
            'owner_with_underscores/repo_with_underscores'
        ]
    }

    @Unroll
    def "should handle invalid instance format '#invalidInstance'"() {
        when:
        new LaminConfig([instance: invalidInstance, api_key: 'test-key'])

        then:
        thrown(IllegalArgumentException)

        where:
        invalidInstance << [
            'single-name',
            'owner/repo/extra',
            'owner/',
            '/repo',
            'owner//repo',
            'owner/ repo',
            'owner /repo',
            'owner/repo '
        ]
    }

    def "should handle custom retry configuration"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            max_retries: 5,
            retry_delay: 200
        ])

        then:
        config.maxRetries == 5
        config.retryDelay == 200
    }

    def "should handle custom Supabase configuration"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            supabase_api_url: 'https://custom.supabase.co',
            supabase_anon_key: 'custom-anon-key'
        ])

        then:
        config.supabaseApiUrl == 'https://custom.supabase.co'
        config.supabaseAnonKey == 'custom-anon-key'
    }

    def "should handle manual transform and run UID configuration"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            transform_uid: 'transform-123',
            run_uid: 'run-456'
        ])

        then:
        config.transformUid == 'transform-123'
        config.runUid == 'run-456'
    }

    def "should handle null transform and run UID configuration"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key'
        ])

        then:
        config.transformUid == null
        config.runUid == null
    }

    def "should mask sensitive data in toString"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'very-secret-api-key',
            supabase_anon_key: 'very-secret-anon-key'
        ])

        then:
        def str = config.toString()
        str.contains('owner/repo')
        str.contains('ve****ey')  // masked api key
        str.contains('ve****ey')  // masked anon key
        !str.contains('very-secret-api-key')
        !str.contains('very-secret-anon-key')
    }

    def "should use parseConfig static method with Map"() {
        given:
        def configMap = [
            instance: 'owner/repo',
            api_key: 'test-key',
            project: 'test-project'
        ]

        when:
        def config = LaminConfig.parseConfig(configMap)

        then:
        config.instance == 'owner/repo'
        config.apiKey == 'test-key'
        config.project == 'test-project'
    }

    def "should allow global artifacts config only"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            artifacts: [
                enabled: true,
                ulabel_uids: ['label1']
            ]
        ])

        then:
        config.artifacts != null
        config.inputArtifacts == null
        config.outputArtifacts == null
    }

    def "should allow direction-specific artifact configs only"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            input_artifacts: [
                enabled: true
            ],
            output_artifacts: [
                enabled: true
            ]
        ])

        then:
        config.artifacts == null
        config.inputArtifacts != null
        config.outputArtifacts != null
    }

    def "should reject mixing global and direction-specific artifact configs"() {
        when:
        new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            artifacts: [enabled: true],
            input_artifacts: [enabled: true]
        ])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Cannot use both 'artifacts' and 'input_artifacts'/'output_artifacts'")
    }

    def "should reject global artifacts with output_artifacts"() {
        when:
        new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            artifacts: [enabled: true],
            output_artifacts: [enabled: true]
        ])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Cannot use both 'artifacts' and 'input_artifacts'/'output_artifacts'")
    }

    def "should parse project_uids as list"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            project_uids: ['proj111111111111', 'proj222222222222']
        ])

        then:
        config.projectUids == ['proj111111111111', 'proj222222222222']
    }

    def "should parse project_uids as single string"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            project_uids: 'proj123456789012'
        ])

        then:
        config.projectUids == ['proj123456789012']
    }

    def "should parse ulabel_uids as list"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            ulabel_uids: ['ulab111111111111', 'ulab222222222222']
        ])

        then:
        config.ulabelUids == ['ulab111111111111', 'ulab222222222222']
    }

    def "should parse ulabel_uids as single string"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            ulabel_uids: 'ulab123456789012'
        ])

        then:
        config.ulabelUids == ['ulab123456789012']
    }

    def "should create nested run config"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            run: [
                project_uids: ['proj111111111111'],
                ulabel_uids: ['ulab111111111111']
            ]
        ])

        then:
        config.run != null
        config.run.projectUids == ['proj111111111111']
        config.run.ulabelUids == ['ulab111111111111']
    }

    def "should create nested transform config"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            transform: [
                project_uids: ['proj222222222222'],
                ulabel_uids: ['ulab222222222222']
            ]
        ])

        then:
        config.transform != null
        config.transform.projectUids == ['proj222222222222']
        config.transform.ulabelUids == ['ulab222222222222']
    }

    def "should create nested api config"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            api: [
                supabase_api_url: 'https://custom.supabase.co',
                supabase_anon_key: 'custom-key',
                max_retries: 5,
                retry_delay: 200
            ]
        ])

        then:
        config.api != null
        config.api.supabaseApiUrl == 'https://custom.supabase.co'
        config.api.supabaseAnonKey == 'custom-key'
        config.api.maxRetries == 5
        config.api.retryDelay == 200
    }

    def "should handle all nested configs together"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            project_uids: ['proj000000000000'],
            ulabel_uids: ['ulab000000000000'],
            run: [
                project_uids: ['proj111111111111'],
                ulabel_uids: ['ulab111111111111']
            ],
            transform: [
                project_uids: ['proj222222222222'],
                ulabel_uids: ['ulab222222222222']
            ],
            api: [
                max_retries: 10,
                retry_delay: 500
            ]
        ])

        then:
        config.projectUids == ['proj000000000000']
        config.ulabelUids == ['ulab000000000000']
        config.run.projectUids == ['proj111111111111']
        config.run.ulabelUids == ['ulab111111111111']
        config.transform.projectUids == ['proj222222222222']
        config.transform.ulabelUids == ['ulab222222222222']
        config.api.maxRetries == 10
        config.api.retryDelay == 500
    }

    def "should show deprecation warning for old project field"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            project: 'test-project'
        ])

        then:
        config.project == 'test-project'
        // Note: We can't easily test the log output, but we verify the field still works
    }

    def "should use deprecated fields when nested api config not provided"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            supabase_api_url: 'https://deprecated.supabase.co',
            supabase_anon_key: 'deprecated-key',
            max_retries: 7,
            retry_delay: 300
        ])

        then:
        config.supabaseApiUrl == 'https://deprecated.supabase.co'
        config.supabaseAnonKey == 'deprecated-key'
        config.maxRetries == 7
        config.retryDelay == 300
    }

    def "should use nested api config values"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            api: [
                supabase_api_url: 'https://nested.supabase.co',
                max_retries: 5
            ]
        ])

        then:
        config.supabaseApiUrl == 'https://nested.supabase.co'
        config.maxRetries == 5
    }

    def "should prefer explicit project_uids over defaults"() {
        when:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            project_uids: ['proj999999999999']
        ])

        then:
        config.projectUids == ['proj999999999999']
    }
}
