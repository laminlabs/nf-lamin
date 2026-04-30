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
 * Typed wrapper around the {@code get-jwt-v1} LaminHub response.
 *
 * <p>Example payload:
 * <pre>
 * { "accessToken": "eyJ..." }
 * </pre>
 */
@CompileStatic
class AccessTokenResponse {

    /** The JWT access token returned by LaminHub. */
    final String accessToken

    AccessTokenResponse(Map<String, Object> raw) {
        if (raw?.containsKey('error')) {
            throw new IllegalStateException("Failed to fetch access token: ${raw.get('error')}")
        }
        String token = raw?.get('accessToken') as String
        if (!token?.trim()) {
            throw new IllegalStateException('Access token is null or empty.')
        }
        this.accessToken = token
    }
}
