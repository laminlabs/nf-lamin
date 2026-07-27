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

    // Publishable anon keys for anonymous access to public instances. These mirror the
    // `sb_publishable_*` keys used by lamindb-setup (core/_hub_client.py).
    private static final Map<String, Map<String, String>> HUB_LOOKUP = [
        prod: [
            webUrl: 'https://lamin.ai',
            apiUrl: 'https://hub.lamin.ai',
            anonKey: 'sb_publishable_YVa4h8hQ-yBhXpfa2cP39w_PhoLW6Nu'
        ],
        staging: [
            webUrl: 'https://staging.laminhub.com',
            apiUrl: 'https://amvrvdwndlqdzgedrqdv.supabase.co',
            anonKey: 'sb_publishable_amVjtilv_Yj4VmGLmxtq6A_sYlLoQx5'
        ],
        'staging-test': [
            webUrl: 'https://staging-test.laminhub.com',
            apiUrl: 'https://iugyyajllqftbpidapak.supabase.co',
            anonKey: 'sb_publishable_XmXroXqTLQw-eeT5kysCww_k8vJv-4L'
        ],
        'prod-test': [
            webUrl: 'https://prod-test.laminhub.com',
            apiUrl: 'https://xtdacpwiqwpbxsatoyrv.supabase.co',
            anonKey: 'sb_publishable_G-pyO5aW6VFErTzJyVvM5w_NAv1_Mf7'
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
