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
package nextflow.lamin

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import nextflow.plugin.BasePlugin
import nextflow.Session
import nextflow.Nextflow
import org.pf4j.PluginWrapper
import nextflow.lamin.config.LaminConfig

/**
 * Implements the Lamin plugins entry point
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
@CompileStatic
class LaminPlugin extends BasePlugin {
    private static LaminConfig config
    private static Session session

    LaminPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }

    /**
     * Get the LaminConfig instance from the Nextflow session
     * If the config is not set, it will create a new instance
     * using the current Nextflow session and environment variables.
     * @return the LaminConfig instance
     */
    @PackageScope
    static LaminConfig getConfig() {
        if (config == null) {
            config = LaminConfig.parseConfig(getSession())
        }
        return config
    }

    /**
     * Get the current Nextflow session.
     * If the session is not set, it will retrieve the current session
     * using `Session.current()`.
     * @return the current Nextflow session
     * @throws IllegalStateException if no valid session is found
     */
    @PackageScope
    static Session getSession() {
        if (session == null) {
            throw new IllegalStateException(
                'LaminPlugin requires a valid Nextflow session. ' +
                'Please ensure you are using it in a valid Nextflow context.'
            )
        }
        return session
    }

    /**
     * Set the Nextflow session for the Lamin plugin.
     * This method should be called once at the start of the Nextflow run.
     * It ensures that the session is not null and does not change once set.
     * @param newSession the new Nextflow session to set
     * @throws IllegalArgumentException if the new session is null
     * @throws IllegalStateException if the session is already set to a different instance
     */
    @PackageScope
    static void setSession(Session newSession) {
        if (newSession == null) {
            throw new IllegalArgumentException('Session cannot be null')
        }
        if (session != null && session != newSession) {
            throw new IllegalStateException('Session already set to a different instance')
        }
        session = newSession
    }
}
