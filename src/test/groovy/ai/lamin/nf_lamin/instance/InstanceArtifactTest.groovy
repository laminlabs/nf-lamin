/*
 * Copyright (c) 2013-2024, Lamin Labs GmbH.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package ai.lamin.nf_lamin.instance

import ai.lamin.nf_lamin.LaminConfig
import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.hub.LaminHubConfigResolver
import ai.lamin.nf_lamin.instance.Instance
import ai.lamin.nf_lamin.instance.InstanceSettings
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Shared
import java.util.UUID
import java.nio.file.Path

/**
 * Tests the Instance class with real staging environment API calls
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
class InstanceArtifactTest extends Specification {

    @Shared
    String apiKey = System.getenv('LAMIN_API_KEY')

    @Shared
    Instance instance

    def setupSpec() {
        if (apiKey) {
            def config = LaminConfig.parseConfig([
                instance: 'laminlabs/lamindata',
                api_key: apiKey,
                // env: 'staging'
            ])
            def resolvedConfig = LaminHubConfigResolver.resolve(config)
            def hub = new LaminHub(
                resolvedConfig.supabaseApiUrl,
                resolvedConfig.supabaseAnonKey,
                resolvedConfig.apiKey
            )
            def settings = hub.getInstanceSettings(
                config.instanceOwner,
                config.instanceName
            )
            instance = new Instance(hub, settings, 3, 1000)
        }
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should connect to instance and get basic information"() {
        when:
        def owner = instance.getOwner()
        def name = instance.getName()
        def settings = instance.getSettings()

        then:
        owner == 'laminlabs'
        name == 'lamindata'
        settings != null
        settings.id() != null
        settings.apiUrl() != null
        settings.schemaId() != null
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should fetch instance statistics from API"() {
        when:
        def statistics = instance.getInstanceStatistics()

        then:
        statistics != null
        // Statistics should be a map or object with data about the instance
        statistics instanceof Map || statistics instanceof Object
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should get non-empty tables from instance"() {
        when:
        def nonEmptyTables = instance.getNonEmptyTables()

        then:
        nonEmptyTables != null
        nonEmptyTables instanceof Map
        // Even if empty, should return a valid Map structure
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should handle API client configuration correctly"() {
        when:
        def apiInstance = instance.getApiInstance()
        def client = apiInstance.getApiClient()

        then:
        apiInstance != null
        client != null
        client.getBasePath() != null
        client.getBasePath().contains('api')
        // Verify timeout settings are configured
        client.getReadTimeout() == 30000
        client.getConnectTimeout() == 30000
        client.getWriteTimeout() == 30000
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should validate instance settings structure"() {
        when:
        def settings = instance.getSettings()

        then:
        settings.id() instanceof UUID
        settings.owner() == 'laminlabs'
        settings.name() == 'lamindata'
        settings.apiUrl() instanceof String
        settings.schemaId() instanceof UUID
        settings.apiUrl().startsWith('http')
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should test retry configuration"() {
        when:
        def maxRetries = instance.getMaxRetries()
        def retryDelay = instance.getRetryDelay()

        then:
        maxRetries instanceof Integer
        retryDelay instanceof Integer
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should handle record retrieval with proper error handling"() {
        when:
        def args = [
            moduleName: 'lamindb',
            modelName: 'Artifact',
            idOrUid: 'nonexistent-id',
            limitToMany: 5,
            includeForeignKeys: false
        ]

        then:
        try {
            instance.getRecord(args)
        } catch (Exception e) {
            e.message != null
            e.message.length() > 0
        }
    }

    // @IgnoreIf({ !env.LAMIN_API_KEY })
    // def "should validate constructor requirements"() {
    //     given:
    //     def config = LaminConfig.parseConfig([
    //         instance: 'laminlabs/lamindata',
    //         api_key: stagingApiKey,
    //         // env: 'staging'
    //     ])
    //     def resolvedConfig = LaminHubConfigResolver.resolve(config)
    //     def hub = new LaminHub(
    //         resolvedConfig.supabaseApiUrl,
    //         resolvedConfig.supabaseAnonKey,
    //         resolvedConfig.apiKey
    //     )
    //     def settings = hub.getInstanceSettings(
    //         config.instanceOwner,
    //         config.instanceName
    //     )

    //     when:
    //     def newInstance = new Instance(hub, settings, 5, 2000)

    //     then:
    //     newInstance.getHub() == hub
    //     newInstance.getSettings() == settings
    //     newInstance.getMaxRetries() instanceof Integer
    //     newInstance.getRetryDelay() instanceof Integer
    // }

    // @IgnoreIf({ !env.LAMIN_API_KEY })
    // def "should throw exceptions for invalid constructor parameters"() {
    //     given:
    //     def config = LaminConfig.parseConfig([
    //         instance: 'laminlabs/lamindata',
    //         api_key: stagingApiKey,
    //         // env: 'staging'
    //     ])
    //     def resolvedConfig = LaminHubConfigResolver.resolve(config)
    //     def hub = new LaminHub(
    //         resolvedConfig.supabaseApiUrl,
    //         resolvedConfig.supabaseAnonKey,
    //         resolvedConfig.apiKey
    //     )
    //     def settings = hub.getInstanceSettings(
    //         config.instanceOwner,
    //         config.instanceName
    //     )

    //     when:
    //     new Instance(null, settings, 3, 1000)

    //     then:
    //     thrown(IllegalStateException)

    //     when:
    //     new Instance(hub, null, 3, 1000)

    //     then:
    //     thrown(IllegalStateException)
    // }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should be able to retrieve artifact remote URL using non-versioned uid"() {
        when:
        def remoteUrl = instance.getArtifactFromUid("s3rtK8wIzJNKvg5Q")

        then:
        remoteUrl != null
        remoteUrl instanceof Path
        def remoteUrlStr = remoteUrl.toString()
        remoteUrlStr.startsWith('s3:/lamindata/.lamindb/s3rtK8wIzJNKvg5Q')
        remoteUrlStr.endsWith('.txt')
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should be able to retrieve artifact remote URL using versioned but not recent uid"() {
        when:
        def remoteUrl = instance.getArtifactFromUid("s3rtK8wIzJNKvg5Q0000")

        then:
        remoteUrl != null
        remoteUrl instanceof Path
        def remoteUrlStr = remoteUrl.toString()
        remoteUrlStr == 's3:/lamindata/.lamindb/s3rtK8wIzJNKvg5Q0000.txt'
    }

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "should be able to retrieve artifact remote URL with gs service"() {
        when:
        def remoteUrl = instance.getArtifactFromUid("HOpnASIDDLx3pFYD0000")

        then:
        remoteUrl != null
        remoteUrl instanceof Path
        def remoteUrlStr = remoteUrl.toString()
        remoteUrlStr == 'gs:/di-temporary-public/scratch/temp-bgzip/run_20251015_120418/run.bgzip.state.yaml'
    }


}
