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

import groovy.transform.CompileStatic
import groovy.transform.CompileStatic
import nextflow.Session
import nextflow.plugin.BasePlugin
import org.pf4j.PluginWrapper

/**
 * The plugin entry point
 */
@CompileStatic
class LaminPlugin extends BasePlugin {

    private static Session _session
    private static LaminConfig _config

    LaminPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }

    /**
     * Set the Nextflow session
     * @param session the session to set
     */
    static void setSession(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null")
        }
        if (_session != null && _session != session) {
            throw new IllegalStateException("Session already set to a different instance")
        }
        _session = session
        // Clear cached config when session changes
        _config = null
    }

    /**
     * Get the current Nextflow session
     * @return the current session
     * @throws IllegalStateException if no session has been set
     */
    static Session getSession() {
        if (_session == null) {
            throw new IllegalStateException("LaminPlugin requires a valid Nextflow session")
        }
        return _session
    }

    /**
     * Get the Lamin configuration for the current session
     * @return the Lamin configuration
     */
    static LaminConfig getConfig() {
        if (_config == null) {
            _config = LaminConfig.parseConfig(getSession())
        }
        return _config
    }
}
