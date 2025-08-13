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
import spock.lang.Shared
import spock.lang.IgnoreIf

import nextflow.lamin.hub.LaminHub
import nextflow.lamin.instance.Instance
import nextflow.lamin.instance.InstanceSettings

/**
 * Integration tests for Staging Environment
 * Tests interaction with the staging laminlabs/lamindata instance
 *
 * Note: Requires the LAMIN_STAGING_API_KEY environment variable to be set
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
class StagingIntegrationTest extends Specification {

    @Shared
    String apiKey = System.getenv('LAMIN_STAGING_API_KEY')

    @Shared
    LaminConfig stagingConfig

    def setupSpec() {
        // Skip tests if no API key is available
        if (apiKey) {
            stagingConfig = new LaminConfig(
                'laminlabs/lamindata',
                apiKey,
                null,
                'staging'
            )
        }
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "should connect to staging LaminHub"() {
        given:
        def hub = new LaminHub(
            stagingConfig.getSupabaseApiUrl(),
            stagingConfig.getSupabaseAnonKey(),
            stagingConfig.getApiKey()
        )

        when:
        def jwt = hub.fetchJwt()

        then:
        jwt != null
        !jwt.trim().isEmpty()
        jwt.startsWith("eyJ")
    }

        @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should retrieve instance settings from staging"() {
        given:
        def hub = new LaminHub(
            stagingConfig.getSupabaseApiUrl(),
            stagingConfig.getSupabaseAnonKey(),
            stagingConfig.getApiKey()
        )

        when:
        def result = hub != null

        then:
        result == true
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "should create Instance from staging settings"() {
        given:
        def hub = new LaminHub(
            stagingConfig.getSupabaseApiUrl(),
            stagingConfig.getSupabaseAnonKey(),
            stagingConfig.getApiKey()
        )
        def settings = hub.fetchInstanceSettings('laminlabs', 'lamindata')

        when:
        def instance = new Instance(hub, settings, 3, 100)

        then:
        instance != null
        instance.hub == hub
        instance.settings == settings
        instance.maxRetries == 3
        instance.retryDelay == 100
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "should validate staging environment URLs"() {
        expect:
        stagingConfig.getWebUrl() == 'https://staging.laminhub.com'
        stagingConfig.getSupabaseApiUrl() == 'https://amvrvdwndlqdzgedrqdv.supabase.co'
        stagingConfig.getSupabaseAnonKey().startsWith('eyJ')
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "should handle staging configuration properly"() {
        when:
        def config = new LaminConfig(
            'laminlabs/lamindata',
            apiKey,
            'test-project',
            'staging'
        )

        then:
        config.getInstance() == 'laminlabs/lamindata'
        config.getInstanceOwner() == 'laminlabs'
        config.getInstanceName() == 'lamindata'
        config.getEnv() == 'staging'
        config.getProject() == 'test-project'
        config.getApiKey() == apiKey
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "should test staging API connectivity"() {
        given:
        def hub = new LaminHub(
            stagingConfig.getSupabaseApiUrl(),
            stagingConfig.getSupabaseAnonKey(),
            stagingConfig.getApiKey()
        )
        def settings = hub.fetchInstanceSettings('laminlabs', 'lamindata')
        def instance = new Instance(hub, settings, 3, 100)

        when:
        def apiInstance = instance.apiInstance

        then:
        apiInstance != null
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "should handle staging retry configuration"() {
        given:
        def customRetries = 5
        def customDelay = 250

        when:
        def config = new LaminConfig(
            'laminlabs/lamindata',
            apiKey,
            null,
            'staging',
            null,
            null,
            customRetries,
            customDelay
        )

        then:
        config.getMaxRetries() == customRetries
        config.getRetryDelay() == customDelay
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "should verify staging environment constants"() {
        expect:
        stagingConfig.getEnv() == 'staging'
        stagingConfig.getWebUrl() != 'https://lamin.ai'
        stagingConfig.getSupabaseApiUrl() != 'https://hub.lamin.ai'
    }
}
