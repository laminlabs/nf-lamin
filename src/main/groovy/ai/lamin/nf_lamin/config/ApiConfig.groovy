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

import ai.lamin.nf_lamin.util.MaskingUtils
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
 *     max_retry_delay = 60000
 *     max_workers = 8
 *   }
 * }
 * </pre>
 */
@CompileStatic
class ApiConfig {

    static final int DEFAULT_MAX_RETRIES     = 3
    static final int DEFAULT_RETRY_DELAY     = 100
    static final int DEFAULT_MAX_RETRY_DELAY = 30000
    static final int DEFAULT_MAX_WORKERS     = 8

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
        Base delay in milliseconds for the first retry (default: 100). Retries use
        exponential backoff with full jitter: attempt N waits a random duration in
        [0, retry_delay * 2^N], capped at max_retry_delay.
    ''')
    final Integer retry_delay

    @ConfigOption
    @Description('''
        Maximum backoff delay in milliseconds between retries (default: 30000).
    ''')
    final Integer max_retry_delay

    @ConfigOption
    @Description('''
        The web URL for the Lamin hub (e.g. https://lamin.ai). Only needed for custom deployments.
    ''')
    final String web_url

    @ConfigOption
    @Description('''
        Maximum number of worker threads used to create artifacts in parallel (default: 8).
    ''')
    final Integer max_workers

    /**
     * Default constructor required for extension point
     */
    ApiConfig() {
        this.supabase_api_url  = null
        this.supabase_anon_key = null
        this.max_retries       = DEFAULT_MAX_RETRIES
        this.retry_delay       = DEFAULT_RETRY_DELAY
        this.max_retry_delay   = DEFAULT_MAX_RETRY_DELAY
        this.web_url           = null
        this.max_workers       = DEFAULT_MAX_WORKERS
    }

    /**
     * Create an ApiConfig from a configuration map.
     *
     * @param opts Configuration map with keys: supabase_api_url, supabase_anon_key,
     *             max_retries, retry_delay, max_retry_delay, max_workers
     */
    ApiConfig(Map opts) {
        this.supabase_api_url  = opts?.supabase_api_url ?: System.getenv('SUPABASE_API_URL')
        this.supabase_anon_key = opts?.supabase_anon_key ?: System.getenv('SUPABASE_ANON_KEY')
        this.max_retries       = opts?.containsKey('max_retries')     ? (opts.max_retries     as Integer) : DEFAULT_MAX_RETRIES
        this.retry_delay       = opts?.containsKey('retry_delay')     ? (opts.retry_delay     as Integer) : DEFAULT_RETRY_DELAY
        this.max_retry_delay   = opts?.containsKey('max_retry_delay') ? (opts.max_retry_delay as Integer) : DEFAULT_MAX_RETRY_DELAY
        this.web_url           = opts?.web_url
        this.max_workers       = opts?.containsKey('max_workers')     ? (opts.max_workers     as Integer) : DEFAULT_MAX_WORKERS
    }

    String getSupabaseApiUrl()  { this.supabase_api_url }
    String getSupabaseAnonKey() { this.supabase_anon_key }
    String getWebUrl()          { this.web_url }

    Integer getMaxRetries()     { this.max_retries     != null ? this.max_retries     : DEFAULT_MAX_RETRIES }
    Integer getRetryDelay()     { this.retry_delay     != null ? this.retry_delay     : DEFAULT_RETRY_DELAY }
    Integer getMaxRetryDelay()  { this.max_retry_delay != null ? this.max_retry_delay : DEFAULT_MAX_RETRY_DELAY }
    Integer getMaxWorkers()     { this.max_workers     != null ? this.max_workers     : DEFAULT_MAX_WORKERS }

    @Override
    String toString() {
        def maskedAnonKey = MaskingUtils.maskValue(supabase_anon_key)
        return "ApiConfig{supabase_api_url='${supabase_api_url}', supabase_anon_key='${maskedAnonKey}', max_retries=${max_retries}, retry_delay=${retry_delay}, max_retry_delay=${max_retry_delay}, web_url='${web_url}', max_workers=${max_workers}}"
    }
}
