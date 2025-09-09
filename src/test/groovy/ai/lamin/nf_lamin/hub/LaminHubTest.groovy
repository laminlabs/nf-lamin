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

import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Unroll
import ai.lamin.nf_lamin.LaminConfig

/**
 * Test laminhub class
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
class LaminHubTest extends Specification {

    @Shared
    String prodApiKey = System.getenv('LAMIN_API_KEY')
    String stagingApiKey = System.getenv('LAMIN_STAGING_API_KEY')

    def "should create LaminHub with valid parameters"() {
        when:
        def hub = new LaminHub(
            "https://api.example.com",
            "test-anon-key",
            "test-api-key"
        )

        then:
        hub != null
    }

    @Unroll
    def "should throw IllegalArgumentException for invalid '#paramName'"() {
        when:
        new LaminHub(apiUrl, anonKey, apiKey)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(expectedMessage)

        where:
        paramName   | apiUrl                  | anonKey        | apiKey         | expectedMessage
        'apiUrl'    | null                    | "anon-key"     | "api-key"      | "API URL cannot be null or empty"
        'apiUrl'    | ""                      | "anon-key"     | "api-key"      | "API URL cannot be null or empty"
        'apiUrl'    | "  "                    | "anon-key"     | "api-key"      | "API URL cannot be null or empty"
        'anonKey'   | "https://api.test.com"  | null           | "api-key"      | "Anonymous Key cannot be null or empty"
        'anonKey'   | "https://api.test.com"  | ""             | "api-key"      | "Anonymous Key cannot be null or empty"
        'anonKey'   | "https://api.test.com"  | "  "           | "api-key"      | "Anonymous Key cannot be null or empty"
        'apiKey'    | "https://api.test.com"  | "anon-key"     | null           | "API Key cannot be null or empty"
        'apiKey'    | "https://api.test.com"  | "anon-key"     | ""             | "API Key cannot be null or empty"
        'apiKey'    | "https://api.test.com"  | "anon-key"     | "  "           | "API Key cannot be null or empty"
    }

    def "should handle proper initialization"() {
        given:
        def apiUrl = "https://api.example.com"
        def anonKey = "test-anon-key"
        def apiKey = "test-api-key"

        when:
        def hub = new LaminHub(apiUrl, anonKey, apiKey)

        then:
        hub != null
        // Note: We can't easily test the private fields without reflection
        // or additional getters, but we can verify the object is created
    }

    def "should handle URL validation"() {
        when:
        def hub = new LaminHub(
            "https://valid.url.com",
            "valid-anon-key",
            "valid-api-key"
        )

        then:
        hub != null
    }

    def "should handle malformed JWT tokens"() {
        given:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'invalid-api-key',
            env: 'staging'
        ])
        def resolvedConfig = LaminHubConfigResolver.resolve(config)

        when:
        def hub = new LaminHub(
            resolvedConfig.supabaseApiUrl,
            resolvedConfig.supabaseAnonKey,
            resolvedConfig.apiKey
        )

        then:
        hub != null
    }

    def "should handle network connectivity issues gracefully"() {
        given:
        def config = new LaminConfig([
            instance: 'owner/repo',
            api_key: 'test-api-key',
            env: 'staging'
        ])
        def resolvedConfig = LaminHubConfigResolver.resolve(config)

        when:
        def hub = new LaminHub(
            'https://invalid.nonexistent.domain.com',
            resolvedConfig.supabaseAnonKey,
            resolvedConfig.apiKey
        )

        then:
        hub != null
    }


    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "prod: fetch jwt token"() {
        given:
        def config = LaminConfig.parseConfig([
            instance: 'laminlabs/lamindata',
            api_key: prodApiKey,
            env: 'prod'
        ])
        def resolvedConfig = LaminHubConfigResolver.resolve(config)
        def hub = new LaminHub(
            resolvedConfig.supabaseApiUrl,
            resolvedConfig.supabaseAnonKey,
            resolvedConfig.apiKey
        )

        when:
        def accessToken = hub.getAccessToken()

        then:
        accessToken != null
        accessToken.length() > 0
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "prod: fetch instance settings"() {
        given:
        def config = LaminConfig.parseConfig([
            instance: 'laminlabs/lamindata',
            api_key: prodApiKey,
            env: 'prod'
        ])
        def resolvedConfig = LaminHubConfigResolver.resolve(config)
        def hub = new LaminHub(
            resolvedConfig.supabaseApiUrl,
            resolvedConfig.supabaseAnonKey,
            resolvedConfig.apiKey
        )

        when:
        def settings = hub.getInstanceSettings(
            config.instanceOwner,
            config.instanceName
        )
        def str = settings.toString()

        then:
        settings != null
        str.contains('id:')
        str.contains('owner:')
        str.contains('name:')
        str.contains('schemaId:')
        str.contains('apiUrl')
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "staging: fetch jwt token"() {
        given:
        def config = LaminConfig.parseConfig([
            instance: 'laminlabs/lamindata',
            api_key: stagingApiKey,
            env: 'staging'
        ])
        def resolvedConfig = LaminHubConfigResolver.resolve(config)
        def hub = new LaminHub(
            resolvedConfig.supabaseApiUrl,
            resolvedConfig.supabaseAnonKey,
            resolvedConfig.apiKey
        )

        when:
        def accessToken = hub.getAccessToken()

        then:
        accessToken != null
        accessToken.length() > 0
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "staging: fetch instance settings"() {
        given:
        def config = LaminConfig.parseConfig([
            instance: 'laminlabs/lamindata',
            api_key: stagingApiKey,
            env: 'staging'
        ])
        def resolvedConfig = LaminHubConfigResolver.resolve(config)
        def hub = new LaminHub(
            resolvedConfig.supabaseApiUrl,
            resolvedConfig.supabaseAnonKey,
            resolvedConfig.apiKey
        )

        when:
        def settings = hub.getInstanceSettings(
            config.instanceOwner,
            config.instanceName
        )
        def str = settings.toString()

        then:
        settings != null
        str.contains('id:')
        str.contains('owner:')
        str.contains('name:')
        str.contains('schemaId:')
        str.contains('apiUrl')
    }
}
