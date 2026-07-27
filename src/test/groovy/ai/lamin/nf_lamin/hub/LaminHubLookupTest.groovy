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

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Test for LaminHubSettings - tests the static environment configuration
 */
class LaminHubLookupTest extends Specification {

    @Unroll
    def "should return correct configuration for environment '#env'"() {
        when:
        def settings = LaminHubSettings.resolve(new ai.lamin.nf_lamin.LaminConfig([instance: 'o/r', api_key: 'k', env: env]))

        then:
        settings != null
        settings.webUrl == expectedWebUrl
        settings.supabaseApiUrl == expectedApiUrl
        settings.supabaseAnonKey != null
        settings.supabaseAnonKey.startsWith('sb_publishable_')

        where:
        env            | expectedWebUrl                      | expectedApiUrl
        'prod'         | 'https://lamin.ai'                 | 'https://hub.lamin.ai'
        'staging'      | 'https://staging.laminhub.com'     | 'https://amvrvdwndlqdzgedrqdv.supabase.co'
        'staging-test' | 'https://staging-test.laminhub.com'| 'https://iugyyajllqftbpidapak.supabase.co'
        'prod-test'    | 'https://prod-test.laminhub.com'   | 'https://xtdacpwiqwpbxsatoyrv.supabase.co'
    }

    def "should throw for invalid environment"() {
        when:
        LaminHubSettings.resolve(new ai.lamin.nf_lamin.LaminConfig([instance: 'o/r', api_key: 'k', env: 'invalid-env']))

        then:
        thrown(IllegalArgumentException)
    }

    def "should return all available environments"() {
        when:
        def envs = LaminHubSettings.getAvailableEnvironments()

        then:
        envs.contains('prod')
        envs.contains('staging')
        envs.contains('staging-test')
        envs.contains('prod-test')
        envs.size() == 4
    }

    @Unroll
    def "should validate environment '#env' as '#isValid'"() {
        when:
        def result = LaminHubSettings.isValidEnvironment(env)

        then:
        result == isValid

        where:
        env            | isValid
        'prod'         | true
        'staging'      | true
        'staging-test' | true
        'prod-test'    | true
        'invalid'      | false
        'production'   | false
        'development'  | false
        null           | false
        ''             | false
    }

    def "should have consistent publishable anon key format for all environments"() {
        when:
        def allEnvs = LaminHubSettings.getAvailableEnvironments()

        then:
        allEnvs.every { env ->
            def settings = LaminHubSettings.resolve(new ai.lamin.nf_lamin.LaminConfig([instance: 'o/r', api_key: 'k', env: env]))
            settings.supabaseAnonKey.startsWith('sb_publishable_')
        }
    }
}
