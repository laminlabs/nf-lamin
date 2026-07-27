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

package ai.lamin.nf_lamin

import java.util.Collections

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Global
import nextflow.Session

import ai.lamin.nf_lamin.hub.InstanceSettings
import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.hub.LaminHubSettings
import ai.lamin.nf_lamin.instance.Instance

/**
 * Shared client layer that owns the LaminHub connection and per-instance clients,
 * decoupling LaminDB access from run tracking.
 *
 * The run manager registers its authenticated hub here; the {@code lamin://} provider
 * uses the same connection to resolve artifact URIs. When nothing is registered, a hub
 * is built lazily from the session config / env, falling back to anonymous access for
 * public instances (e.g. {@code laminlabs/lamindata}).
 */
@Slf4j
@CompileStatic
final class LaminConnection {

    private static final LaminConnection INSTANCE = new LaminConnection()

    private volatile LaminHub hub
    private volatile LaminConfig config

    // Cache of Instance clients keyed by "owner/name"
    private final Map<String, Instance> instanceCache = Collections.synchronizedMap(new LinkedHashMap<String, Instance>())

    private LaminConnection() {
    }

    static LaminConnection getInstance() {
        return INSTANCE
    }

    /**
     * Register the hub and config built by the run manager so the file-system provider
     * reuses the same authenticated connection and instance cache.
     *
     * @param config the resolved LaminConfig
     * @param hub the authenticated LaminHub
     */
    synchronized void register(LaminConfig config, LaminHub hub) {
        this.config = config
        this.hub = hub
        this.instanceCache.clear()
    }

    synchronized void reset() {
        this.hub = null
        this.config = null
        this.instanceCache.clear()
    }

    /**
     * Get the LaminHub client, building an anonymous-capable one if none is registered.
     */
    LaminHub getHub() {
        ensureInitialized()
        return hub
    }

    /**
     * Get the LaminConfig backing the current connection.
     */
    LaminConfig getConfig() {
        ensureInitialized()
        return config
    }

    /**
     * Get or create a cached Instance client for the specified LaminDB instance.
     *
     * @param owner the owner (user or organization) of the instance
     * @param name the name of the instance
     * @return an Instance client, either from cache or newly created
     */
    Instance getInstance(String owner, String name) {
        ensureInitialized()
        String cacheKey = "${owner}/${name}"
        return instanceCache.computeIfAbsent(cacheKey) { String key ->
            log.debug "Creating new Instance for ${key}"
            InstanceSettings settings = hub.getInstanceSettings(owner, name)
            return new Instance(
                hub,
                settings,
                config.apiConfig.maxRetries,
                config.apiConfig.retryDelay,
                config.apiConfig.maxRetryDelay
            )
        }
    }

    /**
     * Lazily build a standalone hub for lamin:// resolution when no run manager has
     * registered one. Credentials come from lamin.api_key / LAMIN_API_KEY when available,
     * otherwise the connection is anonymous.
     */
    private synchronized void ensureInitialized() {
        if (hub != null) {
            return
        }
        Session session = Global.session as Session
        LaminConfig cfg = LaminConfig.parseConfigForResolution(session)
        LaminHubSettings settings = LaminHubSettings.resolve(cfg)
        boolean anonymous = !cfg.apiKey?.trim()
        log.debug "Creating standalone LaminHub for lamin:// resolution (anonymous=${anonymous})"
        this.config = cfg
        this.hub = new LaminHub(settings.supabaseApiUrl, settings.supabaseAnonKey, cfg.apiKey)
    }
}
