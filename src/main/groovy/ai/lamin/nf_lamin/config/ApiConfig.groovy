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
import nextflow.config.schema.ConfigOption
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
    final String supabaseApiUrl

    @ConfigOption
    @Description('''
        The Supabase Anon Key for the Lamin API.
    ''')
    final String supabaseAnonKey

    @ConfigOption
    @Description('''
        Maximum number of retries for API requests (default: 3).
    ''')
    final Integer maxRetries

    @ConfigOption
    @Description('''
        Delay between retries for API requests in milliseconds (default: 100).
    ''')
    final Integer retryDelay

    /**
     * Default constructor required for extension point
     */
    ApiConfig() {
        this.supabaseApiUrl = null
        this.supabaseAnonKey = null
        this.maxRetries = 3
        this.retryDelay = 100
    }

    /**
     * Create an ApiConfig from a configuration map.
     *
     * @param opts Configuration map with keys: supabase_api_url, supabase_anon_key, max_retries, retry_delay
     */
    ApiConfig(Map opts) {
        this.supabaseApiUrl = opts?.supabase_api_url ?: System.getenv('SUPABASE_API_URL')
        this.supabaseAnonKey = opts?.supabase_anon_key ?: System.getenv('SUPABASE_ANON_KEY')
        this.maxRetries = opts?.containsKey('max_retries') ? (opts.max_retries as Integer) : ((System.getenv('LAMIN_MAX_RETRIES') as Integer) ?: 3)
        this.retryDelay = opts?.containsKey('retry_delay') ? (opts.retry_delay as Integer) : ((System.getenv('LAMIN_RETRY_DELAY') as Integer) ?: 100)
    }

    /**
     * Get the Supabase API URL
     * @return The Supabase API URL
     */
    String getSupabaseApiUrl() {
        return this.supabaseApiUrl
    }

    /**
     * Get the Supabase Anon Key
     * @return The Supabase Anon Key
     */
    String getSupabaseAnonKey() {
        return this.supabaseAnonKey
    }

    /**
     * Get the maximum number of retries
     * @return The maximum number of retries
     */
    Integer getMaxRetries() {
        return this.maxRetries ?: 3
    }

    /**
     * Get the delay between retries
     * @return The delay between retries in milliseconds
     */
    Integer getRetryDelay() {
        return this.retryDelay ?: 100
    }

    @Override
    String toString() {
        def maskedAnonKey = supabaseAnonKey?.size() > 6 ? supabaseAnonKey[0..1] + '****' + supabaseAnonKey[-2..-1] : 'an****ed'
        return "ApiConfig{supabaseApiUrl='${supabaseApiUrl}', supabaseAnonKey='${maskedAnonKey}', maxRetries=${maxRetries}, retryDelay=${retryDelay}}"
    }
}
