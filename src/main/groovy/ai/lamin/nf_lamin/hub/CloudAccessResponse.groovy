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
import groovy.util.logging.Slf4j

import java.time.Instant

/**
 * Typed wrapper around the {@code get-cloud-access-v1} LaminHub response.
 *
 * <p>Example payload:
 * <pre>
 * {
 *   "Credentials": {
 *     "AccessKeyId": "ASIA...",
 *     "SecretAccessKey": "...",
 *     "SessionToken": "...",
 *     "Expiration": "2026-04-28T19:10:13.000Z"
 *   },
 *   "StorageAccessibility": {
 *     "storageRoot": "s3://lamin-us-east-1/JwMEKs04D9WJ",
 *     "role": "write",
 *     "isPublic": false,
 *     "isManaged": true
 *   }
 * }
 * </pre>
 *
 * <p>The object is immutable. Call {@link #isCacheExpired()} to check whether the
 * STS session has expired (within a 5-minute safety margin) before re-using cached
 * credentials. {@link #isUsable()} returns true only when all three credential
 * fields are present.
 */
@Slf4j
@CompileStatic
class CloudAccessResponse {

    // --- Credentials ---
    final String accessKeyId
    final String secretAccessKey
    final String sessionToken
    /** STS token expiry. May be {@code null} if the server did not return an Expiration field. */
    final Instant expiration

    // --- StorageAccessibility ---
    final String storageRoot
    final String role
    final boolean publicAccess
    final boolean managedAccess

    /** Epoch ms after which this response should not be served from cache. */
    private final long cacheExpiresAt

    CloudAccessResponse(Map<String, Object> raw) {
        Map<String, Object> creds = raw?.get('Credentials') as Map<String, Object>
        if (creds) {
            this.accessKeyId = creds.get('AccessKeyId') as String
            this.secretAccessKey = creds.get('SecretAccessKey') as String
            this.sessionToken = creds.get('SessionToken') as String

            String expirationStr = creds.get('Expiration') as String
            Instant exp = null
            if (expirationStr) {
                try {
                    exp = Instant.parse(expirationStr)
                } catch (Exception e) {
                    log.debug "Could not parse Expiration from cloud credentials: ${e.message}"
                }
            }
            this.expiration = exp
        } else {
            this.accessKeyId = null
            this.secretAccessKey = null
            this.sessionToken = null
            this.expiration = null
        }

        Map<String, Object> accessibility = raw?.get('StorageAccessibility') as Map<String, Object>
        if (accessibility) {
            this.storageRoot = accessibility.get('storageRoot') as String
            this.role = accessibility.get('role') as String
            this.publicAccess = (accessibility.get('isPublic') as Boolean) ?: false
            this.managedAccess = (accessibility.get('isManaged') as Boolean) ?: false
        } else {
            this.storageRoot = null
            this.role = null
            this.publicAccess = false
            this.managedAccess = false
        }

        // Cache until 5 minutes before the STS expiry, or 55 minutes from now if no expiry given.
        if (this.expiration != null) {
            this.cacheExpiresAt = this.expiration.toEpochMilli() - 5L * 60 * 1000
        } else {
            this.cacheExpiresAt = System.currentTimeMillis() + 55L * 60 * 1000
        }
    }

    /** Returns true when the response contains a full set of STS credentials. */
    boolean hasCredentials() {
        return accessKeyId != null
    }

    /** Returns true when all three credential fields are non-empty and usable for S3 auth. */
    boolean isUsable() {
        return accessKeyId && secretAccessKey && sessionToken
    }

    /**
     * Returns true when the cached response has passed its freshness window (STS expiry minus
     * 5 minutes) and a fresh call to LaminHub should be made.
     */
    boolean isCacheExpired() {
        return System.currentTimeMillis() >= cacheExpiresAt
    }
}
