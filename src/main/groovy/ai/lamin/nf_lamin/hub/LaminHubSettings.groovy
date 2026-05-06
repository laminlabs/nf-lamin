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

package ai.lamin.nf_lamin.hub

import groovy.transform.CompileStatic
import ai.lamin.nf_lamin.LaminConfig

/**
 * Resolved hub settings for a Lamin environment, combining environment-specific
 * hub configuration with any explicit overrides from the user's LaminConfig.
 */
@CompileStatic
class LaminHubSettings {

    private static final Map<String, Map<String, String>> HUB_LOOKUP = [
        prod: [
            webUrl: 'https://lamin.ai',
            apiUrl: 'https://hub.lamin.ai',
            anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxhZXNhdW1tZHlkbGxwcGdmY2h1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2NTY4NDA1NTEsImV4cCI6MTk3MjQxNjU1MX0.WUeCRiun0ExUxKIv5-CtjF6878H8u26t0JmCWx3_2-c'
        ],
        staging: [
            webUrl: 'https://staging.laminhub.com',
            apiUrl: 'https://amvrvdwndlqdzgedrqdv.supabase.co',
            anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFtdnJ2ZHduZGxxZHpnZWRycWR2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2NzcxNTcxMzMsImV4cCI6MTk5MjczMzEzM30.Gelt3dQEi8tT4j-JA36RbaZuUvxRnczvRr3iyRtzjY0'
        ],
        'staging-test': [
            webUrl: 'https://staging-test.laminhub.com',
            apiUrl: 'https://iugyyajllqftbpidapak.supabase.co',
            anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml1Z3l5YWpsbHFmdGJwaWRhcGFrIiwicm9sZSI6ImFub24iLCJpYXQiOjE2OTQyMjYyODMsImV4cCI6MjAwOTgwMjI4M30.s7B0gMogFhUatMSwlfuPJ95kWhdCZMn1ROhZ3t6Og90'
        ],
        'prod-test': [
            webUrl: 'https://prod-test.laminhub.com',
            apiUrl: 'https://xtdacpwiqwpbxsatoyrv.supabase.co',
            anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh0ZGFjcHdpcXdwYnhzYXRveXJ2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2OTQyMjYxNDIsImV4cCI6MjAwOTgwMjE0Mn0.Dbi27qujTt8Ei9gfp9KnEWTYptE5KUbZzEK6boL46k4'
        ]
    ]

    final String instance
    final String apiKey
    final String env
    final Integer maxRetries
    final Integer retryDelay
    final String supabaseApiUrl
    final String supabaseAnonKey
    final String webUrl

    private LaminHubSettings(
        String instance,
        String apiKey,
        String env,
        Integer maxRetries,
        Integer retryDelay,
        String supabaseApiUrl,
        String supabaseAnonKey,
        String webUrl
    ) {
        this.instance = instance
        this.apiKey = apiKey
        this.env = env
        this.maxRetries = maxRetries
        this.retryDelay = retryDelay
        this.supabaseApiUrl = supabaseApiUrl
        this.supabaseAnonKey = supabaseAnonKey
        this.webUrl = webUrl
    }

    /**
     * Resolve hub settings from a LaminConfig, merging environment-specific hub
     * defaults with any explicit overrides in the config's api section.
     *
     * @param config the LaminConfig to resolve settings from
     * @return a fully resolved LaminHubSettings instance
     */
    static LaminHubSettings resolve(LaminConfig config) {
        // Validate environment if specified
        if (config.env && !isValidEnvironment(config.env)) {
            throw new IllegalArgumentException("Provided environment '${config.env}' is not valid. Please provide a valid environment: ${availableEnvironments.join(', ')}.")
        }

        // Start from hub defaults for the environment (or prod)
        Map<String, String> hubConfig = HUB_LOOKUP[config.env ?: 'prod']

        // Explicit api config overrides hub defaults
        String supabaseApiUrl = config.apiConfig.supabaseApiUrl ?: hubConfig['apiUrl']
        String supabaseAnonKey = config.apiConfig.supabaseAnonKey ?: hubConfig['anonKey']
        String webUrl = config.apiConfig.webUrl ?: hubConfig['webUrl']

        return new LaminHubSettings(
            config.instance,
            config.apiKey,
            config.env,
            config.apiConfig.maxRetries,
            config.apiConfig.retryDelay,
            supabaseApiUrl,
            supabaseAnonKey,
            webUrl
        )
    }

    /**
     * Get all available environment names.
     */
    static Set<String> getAvailableEnvironments() {
        return HUB_LOOKUP.keySet()
    }

    /**
     * Check whether the given environment name is valid.
     */
    static boolean isValidEnvironment(String env) {
        return HUB_LOOKUP.containsKey(env)
    }
}
