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

/**
 * Hub lookup configuration for Lamin environments
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
@CompileStatic
class LaminHubLookup {

    private static final Map<String, Map<String, String>> HUB_LOOKUP = [
        prod: [
            webUrl: 'https://lamin.ai',
            apiUrl: 'https://hub.lamin.ai',
            anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxhZXNhdW1tZHlkbGxwcGdmY2h1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2NTY4NDA1NTEsImV4cCI6MTk3MjQxNjU1MX0.WUeCRiun0ExUxKIv5-CtjF6878H8u26t0JmCWx3_2-c'
        ],
        staging: [
            webUrl: "https://staging.laminhub.com",
            apiUrl: 'https://amvrvdwndlqdzgedrqdv.supabase.co',
            anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFtdnJ2ZHduZGxxZHpnZWRycWR2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2NzcxNTcxMzMsImV4cCI6MTk5MjczMzEzM30.Gelt3dQEi8tT4j-JA36RbaZuUvxRnczvRr3iyRtzjY0'
        ],
        'staging-test': [
            webUrl: "https://staging-test.laminhub.com",
            apiUrl: 'https://iugyyajllqftbpidapak.supabase.co',
            anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml1Z3l5YWpsbHFmdGJwaWRhcGFrIiwicm9sZSI6ImFub24iLCJpYXQiOjE2OTQyMjYyODMsImV4cCI6MjAwOTgwMjI4M30.s7B0gMogFhUatMSwlfuPJ95kWhdCZMn1ROhZ3t6Og90'
        ],
        'prod-test': [
            webUrl: "https://prod-test.laminhub.com",
            apiUrl: 'https://xtdacpwiqwpbxsatoyrv.supabase.co',
            anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh0ZGFjcHdpcXdwYnhzYXRveXJ2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2OTQyMjYxNDIsImV4cCI6MjAwOTgwMjE0Mn0.Dbi27qujTt8Ei9gfp9KnEWTYptE5KUbZzEK6boL46k4'
        ]
    ]

    /**
     * Get the configuration for a specific environment
     * @param env the environment name
     * @return the configuration map for the environment
     */
    static Map<String, String> getConfig(String env) {
        return HUB_LOOKUP[env]
    }

    /**
     * Get all available environments
     * @return the set of available environment names
     */
    static Set<String> getAvailableEnvironments() {
        return HUB_LOOKUP.keySet()
    }

    /**
     * Check if an environment is valid
     * @param env the environment name to check
     * @return true if the environment is valid, false otherwise
     */
    static boolean isValidEnvironment(String env) {
        return HUB_LOOKUP.containsKey(env)
    }
}
