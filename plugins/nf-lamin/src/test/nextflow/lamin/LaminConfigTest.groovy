package nextflow.lamin

import nextflow.Session
import spock.lang.Specification
import spock.lang.Unroll

class LaminConfigSpec extends Specification {

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
}
