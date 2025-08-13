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
package nextflow.lamin

import spock.lang.Specification
import spock.lang.Unroll
import nextflow.Session

/**
 * Test config data structures and parsing
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
class ConfigurationParsingTest extends Specification {

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
}
