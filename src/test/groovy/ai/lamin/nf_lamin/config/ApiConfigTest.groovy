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

import spock.lang.Specification

/**
 * Test for ApiConfig
 */
class ApiConfigTest extends Specification {

    def "should create config with default values"() {
        when:
        def config = new ApiConfig()

        then:
        config.supabaseApiUrl == null
        config.supabaseAnonKey == null
        config.maxRetries == 3
        config.retryDelay == 100
    }

    def "should create config with empty map"() {
        when:
        def config = new ApiConfig([:])

        then:
        config.supabaseApiUrl == null
        config.supabaseAnonKey == null
        config.maxRetries == 3
        config.retryDelay == 100
    }

    def "should create config with custom Supabase settings"() {
        when:
        def config = new ApiConfig([
            supabase_api_url: 'https://custom.supabase.co',
            supabase_anon_key: 'custom-anon-key'
        ])

        then:
        config.supabaseApiUrl == 'https://custom.supabase.co'
        config.supabaseAnonKey == 'custom-anon-key'
        config.maxRetries == 3
        config.retryDelay == 100
    }

    def "should create config with custom retry settings"() {
        when:
        def config = new ApiConfig([
            max_retries: 5,
            retry_delay: 200
        ])

        then:
        config.supabaseApiUrl == null
        config.supabaseAnonKey == null
        config.maxRetries == 5
        config.retryDelay == 200
    }

    def "should create config with all custom settings"() {
        when:
        def config = new ApiConfig([
            supabase_api_url: 'https://test.supabase.co',
            supabase_anon_key: 'test-key',
            max_retries: 10,
            retry_delay: 500
        ])

        then:
        config.supabaseApiUrl == 'https://test.supabase.co'
        config.supabaseAnonKey == 'test-key'
        config.maxRetries == 10
        config.retryDelay == 500
    }

    def "should handle null config map"() {
        when:
        def config = new ApiConfig(null)

        then:
        config.supabaseApiUrl == null
        config.supabaseAnonKey == null
        config.maxRetries == 3
        config.retryDelay == 100
    }

    def "should prefer explicit config over defaults"() {
        when:
        def config = new ApiConfig([
            supabase_api_url: 'https://explicit.supabase.co',
            supabase_anon_key: 'explicit-key',
            max_retries: 5,
            retry_delay: 200
        ])

        then:
        config.supabaseApiUrl == 'https://explicit.supabase.co'
        config.supabaseAnonKey == 'explicit-key'
        config.maxRetries == 5
        config.retryDelay == 200
    }

    def "should handle explicit null values"() {
        when:
        def config = new ApiConfig([
            supabase_api_url: null,
            supabase_anon_key: null,
            max_retries: null,
            retry_delay: null
        ])

        then:
        config.supabaseApiUrl == null
        config.supabaseAnonKey == null
        config.maxRetries == 3  // falls back to default
        config.retryDelay == 100  // falls back to default
    }

    def "should handle explicit zero as valid value"() {
        when:
        def config = new ApiConfig([
            max_retries: 0,
            retry_delay: 0
        ])

        then:
        config.maxRetries == 0
        config.retryDelay == 0
        // Note: containsKey check in constructor allows 0 to override defaults
    }

    def "should provide getter methods"() {
        given:
        def config = new ApiConfig([
            supabase_api_url: 'https://test.supabase.co',
            supabase_anon_key: 'test-key',
            max_retries: 5,
            retry_delay: 200
        ])

        expect:
        config.getSupabaseApiUrl() == 'https://test.supabase.co'
        config.getSupabaseAnonKey() == 'test-key'
        config.getMaxRetries() == 5
        config.getRetryDelay() == 200
    }
}
