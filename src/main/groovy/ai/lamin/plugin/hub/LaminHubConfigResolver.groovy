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

package ai.lamin.plugin.hub

import groovy.transform.CompileStatic
import ai.lamin.plugin.LaminConfig

/**
 * Hub configuration resolver that enhances LaminConfig with hub-specific settings
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
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
        resolved.project = config.project
        resolved.env = config.env
        resolved.maxRetries = config.maxRetries
        resolved.retryDelay = config.retryDelay

        // Start with configured values
        resolved.supabaseApiUrl = config.supabaseApiUrl
        resolved.supabaseAnonKey = config.supabaseAnonKey

        // If environment is specified and hub values are not explicitly configured, use hub lookup
        if (config.env) {
            // Validate environment
            if (!LaminHubLookup.isValidEnvironment(config.env)) {
                throw new IllegalArgumentException("Provided environment '${config.env}' is not valid. Please provide a valid environment: ${LaminHubLookup.availableEnvironments.join(', ')}.")
            }

            // Get hub configuration for the environment
            Map<String, String> hubConfig = LaminHubLookup.getConfig(config.env)
            if (hubConfig) {
                // Only override if not explicitly configured
                resolved.supabaseApiUrl = config.supabaseApiUrl ?: hubConfig['apiUrl']
                resolved.supabaseAnonKey = config.supabaseAnonKey ?: hubConfig['anonKey']
                resolved.webUrl = hubConfig['webUrl']
            }
        } else {
            // Default to prod environment if none specified
            Map<String, String> hubConfig = LaminHubLookup.getConfig('prod')
            if (hubConfig) {
                resolved.supabaseApiUrl = config.supabaseApiUrl ?: hubConfig['apiUrl']
                resolved.supabaseAnonKey = config.supabaseAnonKey ?: hubConfig['anonKey']
                resolved.webUrl = hubConfig['webUrl']
            }
        }

        return resolved
    }

    /**
     * Get the web URL for the given environment
     * @param env the environment name (defaults to 'prod' if null)
     * @return the web URL for the environment
     */
    static String getWebUrl(String env) {
        Map<String, String> hubConfig = LaminHubLookup.getConfig(env ?: 'prod')
        return hubConfig ? hubConfig['webUrl'] : null
    }
}
