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
 * Integration tests for Production Environment
 * Tests retrieval of information from the production laminlabs/lamindata instance
 *
 * NOTE: Requires the LAMIN_API_KEY environment variable to be set!
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
class ProductionIntegrationTest extends Specification {

    @Shared
    String apiKey = System.getenv('LAMIN_API_KEY')

    @Shared
    LaminConfig prodConfig

    def setupSpec() {
        // Skip tests if no API key is available
        if (apiKey) {
            prodConfig = new LaminConfig(
                'laminlabs/lamindata',
                apiKey,
                null,
                'prod'
            )
        }
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should connect to production LaminHub"() {
        given:
        def hub = new LaminHub(
            prodConfig.getSupabaseApiUrl(),
            prodConfig.getSupabaseAnonKey(),
            prodConfig.getApiKey()
        )

        when:
        def result = hub != null

        then:
        result == true
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should retrieve instance settings from production"() {
        given:
        def hub = new LaminHub(
            prodConfig.getSupabaseApiUrl(),
            prodConfig.getSupabaseAnonKey(),
            prodConfig.getApiKey()
        )

        when:
        def result = hub != null

        then:
        result == true
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should create Instance from production settings"() {
        when:
        def result = prodConfig != null

        then:
        result == true
        prodConfig.getInstance() == 'laminlabs/lamindata'
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should handle production API configuration validation"() {
        when:
        def result = prodConfig.getInstanceOwner() == 'laminlabs' &&
                    prodConfig.getInstanceName() == 'lamindata'

        then:
        result == true
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should validate production environment URLs"() {
        expect:
        prodConfig.getWebUrl() == 'https://lamin.ai'
        prodConfig.getSupabaseApiUrl() == 'https://hub.lamin.ai'
        prodConfig.getSupabaseAnonKey().startsWith('eyJ')
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should handle production configuration from environment"() {
        given:
        def currentInstance = System.getenv('LAMIN_CURRENT_INSTANCE')
        def currentProject = System.getenv('LAMIN_CURRENT_PROJECT')

        when:
        def config = new LaminConfig(
            'laminlabs/lamindata',
            apiKey,
            currentProject,
            'prod'
        )

        then:
        config.getInstance() == 'laminlabs/lamindata'
        config.getInstanceOwner() == 'laminlabs'
        config.getInstanceName() == 'lamindata'
        config.getEnv() == 'prod'
        config.getApiKey() == apiKey
    }
}
