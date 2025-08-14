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
package nextflow.lamin.config

import nextflow.Session
import spock.lang.Specification
import spock.lang.Unroll

class LaminConfigTest extends Specification {

    def "should create a valid config with required parameters"() {
        when:
        def config = new LaminConfig("owner/repo", "test-api-key")

        then:
        config.getInstance() == "owner/repo"
        config.getApiKey() == "test-api-key"
        config.getProject() == null
        config.getEnv() == null
        config.getMaxRetries() == 3
        config.getRetryDelay() == 100
        config.getWebUrl() == 'https://lamin.ai'
    }

    def "should create a valid config with all parameters provided"() {
        when:
        def config = new LaminConfig(
            "owner/repo",
            "test-api-key",
            "my-project",
            "staging",
            "http://custom.supabase.co",
            "custom-anon-key",
            5,
            200
        )

        then:
        config.getInstance() == "owner/repo"
        config.getApiKey() == "test-api-key"
        config.getProject() == "my-project"
        config.getEnv() == "staging"
        config.getSupabaseApiUrl() == "http://custom.supabase.co"
        config.getSupabaseAnonKey() == "custom-anon-key"
        config.getMaxRetries() == 5
        config.getRetryDelay() == 200
    }

    def "should correctly use hub lookup values for a given environment"() {
        when:
        def config = new LaminConfig("owner/repo", "test-api-key", null, "staging")

        then:
        config.getEnv() == "staging"
        config.getSupabaseApiUrl() == 'https://amvrvdwndlqdzgedrqdv.supabase.co'
        config.getSupabaseAnonKey() == 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFtdnJ2ZHduZGxxZHpnZWRycWR2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2NzcxNTcxMzMsImV4cCI6MTk5MjczMzEzM30.Gelt3dQEi8tT4j-JA36RbaZuUvxRnczvRr3iyRtzjY0'
        config.getWebUrl() == "https://staging.laminhub.com"
    }

    @Unroll
    def "should throw IllegalArgumentException for invalid instance '#instance'"() {
        when:
        new LaminConfig(instance, "test-api-key")

        then:
        thrown(IllegalArgumentException)

        where:
        instance << [null, "", " ", "invalid-instance", "owner/repo/extra"]
    }

    @Unroll
    def "should throw IllegalArgumentException for invalid api key '#apiKey'"() {
        when:
        new LaminConfig("owner/repo", apiKey)

        then:
        thrown(IllegalArgumentException)

        where:
        apiKey << [null, "", " "]
    }

    def "should throw IllegalArgumentException for an invalid environment"() {
        when:
        new LaminConfig("owner/repo", "test-api-key", null, "invalid-env")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("Provided environment 'invalid-env' is not valid.")
    }

    def "should correctly parse instance owner and name"() {
        given:
        def config = new LaminConfig("test-owner/test-repo", "test-api-key")

        expect:
        config.getInstanceOwner() == "test-owner"
        config.getInstanceName() == "test-repo"
    }

    def "should parse config from the session map"() {
        given:
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'map/instance',
                    api_key: 'map-key',
                    project: 'map-project',
                    env: 'staging',
                    max_retries: 10,
                    retry_delay: 500,
                    supabase_api_url: 'http://map.supabase.co',
                    supabase_anon_key: 'map-anon-key'
                ]
            ]
        }

        when:
        def config = LaminConfig.parseConfig(session)

        then:
        config.getInstance() == 'map/instance'
        config.getApiKey() == 'map-key'
        config.getProject() == 'map-project'
        config.getEnv() == 'staging'
        config.getMaxRetries() == 10
        config.getRetryDelay() == 500
        config.getSupabaseApiUrl() == 'http://map.supabase.co'
        config.getSupabaseAnonKey() == 'map-anon-key'
    }

    def "should use default values when parsing config"() {
        given:
        // We must provide the required fields
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'owner/repo',
                    api_key: 'some-key'
                ]
            ]
        }

        when:
        // We can't easily mock System.getenv, so this test assumes they are not set
        // and checks if the defaults from parseConfig are applied.
        def config = LaminConfig.parseConfig(session)

        then:
        config.getInstance() == 'owner/repo'
        config.getApiKey() == 'some-key'
        config.getProject() == null
        config.getEnv() == 'prod'
        config.getMaxRetries() == 3
        config.getRetryDelay() == 100
    }

    def "should parse minimal configuration"() {
        given:
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'owner/instance',
                    api_key: 'test-key'
                ]
            ]
        }

        when:
        def config = LaminConfig.parseConfig(session)

        then:
        config.getInstance() == 'owner/instance'
        config.getApiKey() == 'test-key'
        config.getProject() == null
        config.getEnv() == 'prod'
        config.getMaxRetries() == 3
        config.getRetryDelay() == 100
    }

    def "should parse complete configuration"() {
        given:
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'owner/instance',
                    api_key: 'test-key',
                    project: 'my-project',
                    env: 'staging',
                    max_retries: 5,
                    retry_delay: 200,
                    supabase_api_url: 'https://custom.supabase.co',
                    supabase_anon_key: 'custom-anon-key'
                ]
            ]
        }

        when:
        def config = LaminConfig.parseConfig(session)

        then:
        config.getInstance() == 'owner/instance'
        config.getApiKey() == 'test-key'
        config.getProject() == 'my-project'
        config.getEnv() == 'staging'
        config.getMaxRetries() == 5
        config.getRetryDelay() == 200
        config.getSupabaseApiUrl() == 'https://custom.supabase.co'
        config.getSupabaseAnonKey() == 'custom-anon-key'
    }

    @Unroll
    def "should validate all environment lookups for '#env'"() {
        given:
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'owner/instance',
                    api_key: 'test-key',
                    env: env
                ]
            ]
        }

        when:
        def config = LaminConfig.parseConfig(session)

        then:
        config.getEnv() == env
        config.getSupabaseApiUrl() == expectedApiUrl
        config.getSupabaseAnonKey() == expectedAnonKey
        config.getWebUrl() == expectedWebUrl

        where:
        env             | expectedApiUrl                                 | expectedAnonKey                                                                                                                                             | expectedWebUrl
        'prod'          | 'https://hub.lamin.ai'                         | 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxhZXNhdW1tZHlkbGxwcGdmY2h1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2NTY4NDA1NTEsImV4cCI6MTk3MjQxNjU1MX0.WUeCRiun0ExUxKIv5-CtjF6878H8u26t0JmCWx3_2-c' | 'https://lamin.ai'
        'staging'       | 'https://amvrvdwndlqdzgedrqdv.supabase.co'      | 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFtdnJ2ZHduZGxxZHpnZWRycWR2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2NzcxNTcxMzMsImV4cCI6MTk5MjczMzEzM30.Gelt3dQEi8tT4j-JA36RbaZuUvxRnczvRr3iyRtzjY0' | 'https://staging.laminhub.com'
        'staging-test'  | 'https://iugyyajllqftbpidapak.supabase.co'      | 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml1Z3l5YWpsbHFmdGJwaWRhcGFrIiwicm9sZSI6ImFub24iLCJpYXQiOjE2OTQyMjYyODMsImV4cCI6MjAwOTgwMjI4M30.s7B0gMogFhUatMSwlfuPJ95kWhdCZMn1ROhZ3t6Og90' | 'https://staging-test.laminhub.com'
        'prod-test'     | 'https://xtdacpwiqwpbxsatoyrv.supabase.co'      | 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh0ZGFjcHdpcXdwYnhzYXRveXJ2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2OTQyMjYxNDIsImV4cCI6MjAwOTgwMjE0Mn0.Dbi27qujTt8Ei9gfp9KnEWTYptE5KUbZzEK6boL46k4' | 'https://prod-test.laminhub.com'
    }

    def "should handle configuration with no lamin section"() {
        given:
        def session = Mock(Session) {
            config >> [:]
        }

        when:
        LaminConfig.parseConfig(session)

        then:
        // throw an exception when required fields are missing
        thrown(IllegalArgumentException)
    }

    def "should handle null session config"() {
        given:
        def session = Mock(Session) {
            config >> null
        }

        when:
        LaminConfig.parseConfig(session)

        then:
        // throw an exception when required fields are missing
        thrown(IllegalArgumentException)
    }

    def "should validate instance format during parsing"() {
        given:
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'invalid-instance-format',
                    api_key: 'test-key'
                ]
            ]
        }

        when:
        LaminConfig.parseConfig(session)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('not valid')
    }

    def "should handle type conversion for numeric values"() {
        given:
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'owner/instance',
                    api_key: 'test-key',
                    max_retries: '10',
                    retry_delay: '500'
                ]
            ]
        }

        when:
        def config = LaminConfig.parseConfig(session)

        then:
        config.getMaxRetries() == 10
        config.getRetryDelay() == 500
    }

    def "should override environment settings with custom URLs"() {
        given:
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'owner/instance',
                    api_key: 'test-key',
                    env: 'staging',
                    supabase_api_url: 'https://custom.api.url',
                    supabase_anon_key: 'custom-anon-key'
                ]
            ]
        }

        when:
        def config = LaminConfig.parseConfig(session)

        then:
        config.getEnv() == 'staging'
        config.getSupabaseApiUrl() == 'https://custom.api.url'
        config.getSupabaseAnonKey() == 'custom-anon-key'
        config.getWebUrl() == 'https://staging.laminhub.com'
    }

    def "should create valid toString representation with masked sensitive data"() {
        given:
        def config = new LaminConfig(
            'owner/instance',
            'api-key-that-should-be-masked',
            'test-project',
            'staging',
            'https://api.url',
            'anon-key-that-should-be-masked',
            5,
            200
        )

        when:
        def result = config.toString()

        then:
        result.contains('owner/instance')
        result.contains('test-project')
        result.contains('staging')
        result.contains('https://api.url')
        result.contains('maxRetries=5')
        result.contains('retryDelay=200')
        result.contains('ap****ed')
        result.contains('an****ed')
        !result.contains('api-key-that-should-be-masked')
        !result.contains('anon-key-that-should-be-masked')
    }

    @Unroll
    def "should handle invalid instance format '#invalidInstance'"() {
        when:
        new LaminConfig(invalidInstance, 'test-api-key')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('not valid')

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

    def "should handle empty and null instances"() {
        when:
        new LaminConfig(emptyInstance, 'test-api-key')

        then:
        thrown(IllegalArgumentException)

        where:
        emptyInstance << ['', null, '/', ' ']
    }

    @Unroll
    def "should accept valid instance format '#validInstance'"() {
        when:
        def config = new LaminConfig(validInstance, 'test-api-key')

        then:
        config.getInstance() == validInstance
        noExceptionThrown()

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

    def "should handle extreme retry values"() {
        when:
        def config = new LaminConfig(
            'owner/repo',
            'test-api-key',
            null,
            null,
            null,
            null,
            maxRetries ?: 3,
            retryDelay ?: 100
        )

        then:
        config.getMaxRetries() == (maxRetries ?: 3)
        config.getRetryDelay() == (retryDelay ?: 100)

        where:
        maxRetries | retryDelay
        null       | null
        1          | 1
        100        | 10000
        Integer.MAX_VALUE | Integer.MAX_VALUE
    }

    def "should handle special characters in configuration"() {
        given:
        def specialKey = 'key-with-special-chars-!@#$%^&*()'
        def specialProject = 'project with spaces & special chars'

        when:
        def config = new LaminConfig(
            'owner/repo',
            specialKey,
            specialProject
        )

        then:
        config.getApiKey() == specialKey
        config.getProject() == specialProject
    }

    def "should handle very long configuration values"() {
        given:
        def longKey = 'a' * 1000
        def longProject = 'b' * 1000

        when:
        def config = new LaminConfig(
            'owner/repo',
            longKey,
            longProject
        )

        then:
        config.getApiKey() == longKey
        config.getProject() == longProject
    }

    def "should handle missing environment variables gracefully"() {
        given:
        def session = Mock(Session) {
            config >> [
                lamin: [:]
            ]
        }

        when:
        LaminConfig.parseConfig(session)

        then:
        thrown(IllegalArgumentException)
    }

    def "should validate environment names strictly"() {
        when:
        new LaminConfig(
            'owner/repo',
            'test-key',
            null,
            invalidEnv
        )

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("not valid")

        where:
        invalidEnv << [
            'production',
            'development',
            'local',
            'test',
            'PROD',
            'Staging',
            'prod-staging'
        ]
    }
}
