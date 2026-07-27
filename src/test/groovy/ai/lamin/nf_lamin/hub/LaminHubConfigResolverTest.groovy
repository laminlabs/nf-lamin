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

package ai.lamin.nf_lamin.hub

import ai.lamin.nf_lamin.LaminConfig
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Test for LaminHubSettings - focuses on hub lookup functionality
 */
class LaminHubConfigResolverTest extends Specification {

    @Unroll
    def "should resolve hub configuration for environment '#env'"() {
        given:
        def baseConfig = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            env: env
        ])

        when:
        def resolved = LaminHubSettings.resolve(baseConfig)

        then:
        resolved.env == env
        resolved.supabaseApiUrl == expectedApiUrl
        resolved.supabaseAnonKey.startsWith('sb_publishable_')
        resolved.webUrl == expectedWebUrl

        where:
        env            | expectedApiUrl                           | expectedWebUrl
        'prod'         | 'https://hub.lamin.ai'                  | 'https://lamin.ai'
        'staging'      | 'https://amvrvdwndlqdzgedrqdv.supabase.co' | 'https://staging.laminhub.com'
        'staging-test' | 'https://iugyyajllqftbpidapak.supabase.co' | 'https://staging-test.laminhub.com'
        'prod-test'    | 'https://xtdacpwiqwpbxsatoyrv.supabase.co' | 'https://prod-test.laminhub.com'
    }

    def "should use prod defaults when no environment specified"() {
        given:
        def baseConfig = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key'
        ])

        when:
        def resolved = LaminHubSettings.resolve(baseConfig)

        then:
        resolved.supabaseApiUrl == 'https://hub.lamin.ai'
        resolved.supabaseAnonKey == 'sb_publishable_YVa4h8hQ-yBhXpfa2cP39w_PhoLW6Nu'
        resolved.webUrl == 'https://lamin.ai'
    }

    def "should preserve custom Supabase configuration over hub defaults"() {
        given:
        def baseConfig = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            env: 'prod',
            api: [
                supabase_api_url: 'https://custom.supabase.co',
                supabase_anon_key: 'custom-anon-key'
            ]
        ])

        when:
        def resolved = LaminHubSettings.resolve(baseConfig)

        then:
        resolved.supabaseApiUrl == 'https://custom.supabase.co'
        resolved.supabaseAnonKey == 'custom-anon-key'
        resolved.webUrl == 'https://lamin.ai'  // webUrl still comes from hub lookup
    }

    def "should throw IllegalArgumentException for invalid environment"() {
        given:
        def baseConfig = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            env: 'invalid-env'
        ])

        when:
        LaminHubSettings.resolve(baseConfig)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('invalid-env')
        ex.message.contains('prod, staging, staging-test, prod-test')
    }

    def "should preserve all base config values"() {
        given:
        def baseConfig = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-key',
            env: 'staging',
            api: [
                max_retries: 5,
                retry_delay: 200
            ]
        ])

        when:
        def resolved = LaminHubSettings.resolve(baseConfig)

        then:
        resolved.instance == 'owner/repo'
        resolved.apiKey == 'test-key'
        resolved.env == 'staging'
        resolved.maxRetries == 5
        resolved.retryDelay == 200
    }

}
