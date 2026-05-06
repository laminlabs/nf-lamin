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
 * Hub configuration resolver that enhances LaminConfig with hub-specific settings
 */
@CompileStatic
class LaminHubConfigResolver {

    /**
     * Resolve hub configuration and enhance the LaminConfig with environment-specific values
     * @param config the base LaminConfig
     * @return enhanced configuration map with resolved hub values
     */
    static Map<String, Object> resolve(LaminConfig config) {
        Map<String, Object> resolved = [:]

        // Copy all existing values
        resolved.instance = config.instance
        resolved.apiKey = config.apiKey
        resolved.env = config.env
        resolved.maxRetries = config.apiConfig.maxRetries
        resolved.retryDelay = config.apiConfig.retryDelay

        // Validate environment if specified
        if (config.env && !LaminHubLookup.isValidEnvironment(config.env)) {
            throw new IllegalArgumentException("Provided environment '${config.env}' is not valid. Please provide a valid environment: ${LaminHubLookup.availableEnvironments.join(', ')}.")
        }

        // Use specified environment or default to prod
        Map<String, String> hubConfig = LaminHubLookup.getConfig(config.env ?: 'prod')
        if (hubConfig) {
            resolved.supabaseApiUrl = hubConfig['apiUrl']
            resolved.supabaseAnonKey = hubConfig['anonKey']
            resolved.webUrl = hubConfig['webUrl']
        }

        if (config.apiConfig.supabaseApiUrl) {
            resolved.supabaseApiUrl = config.apiConfig.supabaseApiUrl
        }
        if (config.apiConfig.supabaseAnonKey) {
            resolved.supabaseAnonKey = config.apiConfig.supabaseAnonKey
        }
        if (config.apiConfig.webUrl) {
            resolved.webUrl = config.apiConfig.webUrl
        }

        return resolved
    }

}
