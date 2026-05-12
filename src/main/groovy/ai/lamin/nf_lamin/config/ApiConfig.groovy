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

package ai.lamin.nf_lamin.config

import groovy.transform.CompileStatic
import nextflow.config.spec.ConfigOption
import nextflow.script.dsl.Description

/**
 * Configuration for API connection settings.
 *
 * This configuration allows specifying advanced API settings such as
 * Supabase URL/key and retry behavior.
 *
 * Example usage in nextflow.config:
 * <pre>
 * lamin {
 *   api {
 *     supabase_api_url = 'https://custom.supabase.co'
 *     supabase_anon_key = 'your-anon-key'
 *     max_retries = 5
 *     retry_delay = 200
 *   }
 * }
 * </pre>
 */
@CompileStatic
class ApiConfig {

    @ConfigOption
    @Description('''
        The Supabase API URL for the Lamin API.
    ''')
    final String supabase_api_url

    @ConfigOption
    @Description('''
        The Supabase Anon Key for the Lamin API.
    ''')
    final String supabase_anon_key

    @ConfigOption
    @Description('''
        Maximum number of retries for API requests (default: 3).
    ''')
    final Integer max_retries

    @ConfigOption
    @Description('''
        Delay between retries for API requests in milliseconds (default: 100).
    ''')
    final Integer retry_delay

    @ConfigOption
    @Description('''
        The web URL for the Lamin hub (e.g. https://lamin.ai). Only needed for custom deployments.
    ''')
    final String web_url

    /**
     * Default constructor required for extension point
     */
    ApiConfig() {
        this.supabase_api_url = null
        this.supabase_anon_key = null
        this.max_retries = 3
        this.retry_delay = 100
        this.web_url = null
    }

    /**
     * Create an ApiConfig from a configuration map.
     *
     * @param opts Configuration map with keys: supabase_api_url, supabase_anon_key, max_retries, retry_delay
     */
    ApiConfig(Map opts) {
        this.supabase_api_url = opts?.supabase_api_url ?: System.getenv('SUPABASE_API_URL')
        this.supabase_anon_key = opts?.supabase_anon_key ?: System.getenv('SUPABASE_ANON_KEY')
        this.max_retries = opts?.containsKey('max_retries') ? (opts.max_retries as Integer) : ((System.getenv('LAMIN_MAX_RETRIES') as Integer) ?: 3)
        this.retry_delay = opts?.containsKey('retry_delay') ? (opts.retry_delay as Integer) : ((System.getenv('LAMIN_RETRY_DELAY') as Integer) ?: 100)
        this.web_url = opts?.web_url
    }

    /**
     * Get the Supabase API URL
     * @return The Supabase API URL
     */
    String getSupabaseApiUrl() {
        return this.supabase_api_url
    }

    /**
     * Get the Supabase Anon Key
     * @return The Supabase Anon Key
     */
    String getSupabaseAnonKey() {
        return this.supabase_anon_key
    }

    /**
     * Get the maximum number of retries
     * @return The maximum number of retries
     */
    Integer getMaxRetries() {
        return this.max_retries != null ? this.max_retries : 3
    }

    /**
     * Get the delay between retries
     * @return The delay between retries in milliseconds
     */
    Integer getRetryDelay() {
        return this.retry_delay != null ? this.retry_delay : 100
    }

    /**
     * Get the web URL
     * @return The web URL
     */
    String getWebUrl() {
        return this.web_url
    }

    @Override
    String toString() {
        def maskedAnonKey = supabase_anon_key?.size() > 6 ? supabase_anon_key[0..1] + '****' + supabase_anon_key[-2..-1] : 'an****ed'
        return "ApiConfig{supabase_api_url='${supabase_api_url}', supabase_anon_key='${maskedAnonKey}', max_retries=${max_retries}, retry_delay=${retry_delay}, web_url='${web_url}'}"
    }
}
