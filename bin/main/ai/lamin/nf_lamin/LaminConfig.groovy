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

import groovy.transform.CompileStatic
import nextflow.Session
import nextflow.config.schema.ConfigOption
import nextflow.config.schema.ConfigScope
import nextflow.config.schema.ScopeName
import nextflow.script.dsl.Description

/**
 * Handle the configuration of the Lamin plugin
 *
 * These settings can be defined in the nextflow config as follows:
 *
 * lamin {
 *   instance = 'laminlabs/lamindata'
 *   api_key = System.getenv('LAMIN_API_KEY')
 *   project = System.getenv('LAMIN_CURRENT_PROJECT')
 *   env = 'prod'
 * }
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
@ScopeName('lamin')
@Description('''
    The `lamin` scope allows you to configure the `nf-lamin` plugin.
''')
@CompileStatic
class LaminConfig implements ConfigScope {

    @ConfigOption
    @Description('''
        The instance for the Lamin API (format: 'owner/repo').
    ''')
    final String instance

    @ConfigOption
    @Description('''
        The access token for the Lamin API.
    ''')
    final String apiKey

    @ConfigOption
    @Description('''
        The project for the Lamin API.
    ''')
    final String project

    @ConfigOption
    @Description('''
        (Advanced) The environment for the Lamin API (default: 'prod').
    ''')
    final String env

    @ConfigOption
    @Description('''
        (Advanced) The Supabase API URL for the Lamin API.
    ''')
    final String supabaseApiUrl

    @ConfigOption
    @Description('''
        (Advanced) The Supabase Anon Key for the Lamin API.
    ''')
    final String supabaseAnonKey

    @ConfigOption
    @Description('''
        (Advanced) Maximum number of retries for API requests (default: 3).
    ''')
    final Integer maxRetries

    @ConfigOption
    @Description('''
        (Advanced) Delay between retries for API requests in milliseconds (default: 100).
    ''')
    final Integer retryDelay

    @ConfigOption
    @Description('''
        (Advanced) Manually specify a transform UID if known. If provided, the plugin will use this existing transform instead of looking up or creating a new one.
    ''')
    final String transformUid

    @ConfigOption
    @Description('''
        (Advanced) Manually specify a run UID if known. If provided, the plugin will use this existing run instead of creating a new one. The run must have status SCHEDULED (-3), otherwise a warning will be logged and a new run will be created.
    ''')
    final String runUid

    /* required by extension point -- do not remove */
    LaminConfig() {}

    /**
     * Configuration for Lamin API integration
     * @param opts the configuration options map
     */
    LaminConfig(Map opts) {
        // Extract values from map or environment variables
        // Use containsKey to distinguish between "not provided" vs "explicitly null/empty"
        this.instance = opts.containsKey('instance') ? opts.instance : System.getenv('LAMIN_CURRENT_INSTANCE')
        this.apiKey = opts.containsKey('api_key') ? opts.api_key : System.getenv('LAMIN_API_KEY')
        this.project = opts.containsKey('project') ? opts.project : System.getenv('LAMIN_CURRENT_PROJECT')
        this.env = opts.containsKey('env') ? (opts.env ?: 'prod') : (System.getenv('LAMIN_ENV') ?: 'prod')
        this.supabaseApiUrl = opts.containsKey('supabase_api_url') ? opts.supabase_api_url : System.getenv('SUPABASE_API_URL')
        this.supabaseAnonKey = opts.containsKey('supabase_anon_key') ? opts.supabase_anon_key : System.getenv('SUPABASE_ANON_KEY')
        this.maxRetries = opts.containsKey('max_retries') ? (opts.max_retries as Integer) : ((System.getenv('LAMIN_MAX_RETRIES') as Integer) ?: 3)
        this.retryDelay = opts.containsKey('retry_delay') ? (opts.retry_delay as Integer) : ((System.getenv('LAMIN_RETRY_DELAY') as Integer) ?: 100)
        this.transformUid = opts.containsKey('transform_uid') ? opts.transform_uid : System.getenv('LAMIN_TRANSFORM_UID')
        this.runUid = opts.containsKey('run_uid') ? opts.run_uid : System.getenv('LAMIN_RUN_UID')

        validateConfiguration()
    }

    /**
     * Configuration for Lamin API integration (direct parameters)
     * @param instance the instance
     * @param apiKey the API key
     * @param project the project (optional)
     * @param env the environment (optional, defaults to 'prod')
     */
    LaminConfig(String instance, String apiKey, String project = null, String env = 'prod') {
        this([
            instance: instance,
            api_key: apiKey,
            project: project,
            env: env
        ])
    }

    /**
     * Validate the configuration parameters
     * @throws IllegalArgumentException if validation fails
     */
    private void validateConfiguration() {
        // Validation
        if (!this.instance?.trim()) {
            throw new IllegalArgumentException('Lamin instance is not set. Please set the "lamin.instance" in your nextflow.config file.')
        }
        if (!this.apiKey?.trim()) {
            throw new IllegalArgumentException('Lamin API key is not set. Please set the "lamin.api_key" in your nextflow.config file.')
        }

        // check if instance is <owner>/<repo>
        if (!this.instance.matches(/^[\w.-]+\/[\w.-]+$/)) {
            throw new IllegalArgumentException("Provided Lamin instance ${this.instance} is not valid. Please provide a valid instance in the format <owner>/<repo>.")
        }
    }

    /**
     * Get the instance for the Lamin API
     * @return the instance
     */
    String getInstance() {
        return this.instance
    }

    /**
     * Get the instance owner for the Lamin API
     * @return the instance owner
     */
    String getInstanceOwner() {
        return this.instance.split('/')[0]
    }

    /**
     * Get the instance name for the Lamin API
     * @return the instance name
     */
    String getInstanceName() {
        return this.instance.split('/')[1]
    }

    /**
     * Get the API key for Lamin Hub
     * @return the API key
     */
    String getApiKey() {
        return this.apiKey
    }

    /**
     * Get the project for the Lamin API
     * @return the project
     */
    String getProject() {
        return this.project
    }

    /**
     * Get the environment for the Lamin API
     * @return the environment
     */
    String getEnv() {
        return this.env
    }

    /**
     * Get the Supabase API URL for the Lamin API
     * @return the Supabase API URL
     */
    String getSupabaseApiUrl() {
        return this.supabaseApiUrl
    }

    /**
     * Get the Supabase Anon Key for the Lamin API
     * @return the Supabase Anon Key
     */
    String getSupabaseAnonKey() {
        return this.supabaseAnonKey
    }

    /**
     * Get the maximum number of retries for API requests
     * @return the maximum number of retries
     */
    Integer getMaxRetries() {
        return this.maxRetries
    }

    /**
     * Get the delay between retries for API requests
     * @return the delay between retries in milliseconds
     */
    Integer getRetryDelay() {
        return this.retryDelay
    }

    /**
     * Get the manually specified transform UID
     * @return the transform UID, or null if not specified
     */
    String getTransformUid() {
        return this.transformUid
    }

    /**
     * Get the manually specified run UID
     * @return the run UID, or null if not specified
     */
    String getRunUid() {
        return this.runUid
    }

    /**
     * Parse configuration from a Nextflow session
     * @param session the Nextflow session
     * @return the parsed LaminConfig
     */
    static LaminConfig parseConfig(Session session) {
        Map configMap = session?.config?.lamin as Map ?: [:]

        if (!configMap.instance && !System.getenv('LAMIN_CURRENT_INSTANCE')) {
            throw new IllegalArgumentException('Lamin instance is not set. Please set the "lamin.instance" in your nextflow.config file.')
        }

        return new LaminConfig(configMap)
    }

    /**
     * Parse configuration from a Map
     * @param configMap the configuration map
     * @return the parsed LaminConfig
     */
    static LaminConfig parseConfig(Map configMap) {
        return new LaminConfig(configMap)
    }

    /**
     * Create a string representation with masked sensitive data
     * @return string representation
     */
    @Override
    String toString() {
        def maskedApiKey = apiKey?.size() > 6 ? apiKey[0..1] + '****' + apiKey[-2..-1] : 'ap****ed'
        def maskedAnonKey = supabaseAnonKey?.size() > 6 ? supabaseAnonKey[0..1] + '****' + supabaseAnonKey[-2..-1] : 'an****ed'

        return "LaminConfig{" +
            "instance='${instance}', " +
            "apiKey='${maskedApiKey}', " +
            "project='${project}', " +
            "env='${env}', " +
            "supabaseApiUrl='${supabaseApiUrl}', " +
            "supabaseAnonKey='${maskedAnonKey}', " +
            "maxRetries=${maxRetries}, " +
            "retryDelay=${retryDelay}, " +
            "transformUid='${transformUid}', " +
            "runUid='${runUid}'" +
            "}"
    }
}
