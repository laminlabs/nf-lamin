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
import groovy.util.logging.Slf4j
import groovyx.gpars.dataflow.DataflowReadChannel
import groovyx.gpars.dataflow.DataflowWriteChannel
import nextflow.Channel
import nextflow.Session
import nextflow.extension.CH
import nextflow.extension.DataflowHelper
import nextflow.plugin.extension.Factory
import nextflow.plugin.extension.Function
import nextflow.plugin.extension.Operator
import nextflow.plugin.extension.PluginExtensionPoint

/**
 * Example plugin extension showing how to implement a basic
 * channel factory method, a channel operator and a custom function.
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 *
 */
@Slf4j
@CompileStatic
class LaminExtension extends PluginExtensionPoint {

    /*
     * A session hold information about current execution of the script
     */
    protected Session session

    /*
     * A Custom config extracted from nextflow.config under lamin tag
     * nextflow.config
     * ---------------
     * lamin {
     *   instance = "laminlabs/lamindata"
     *   access_token = System.getenv("LAMIN_API_KEY")
     * }
     */
     protected LaminConfig config

    /**
     * An extension that is automatically initialized by nextflow when the session is ready.
     *
     * @param session A nextflow session instance.
     */
    @Override
    protected void init(Session session) {
        this.session = session

        try {
            this.config = new LaminConfig(session)
        } catch (IllegalArgumentException | AssertionError exc) {
            log.error("${exc.getMessage()}")
            log.error("Aborting.")
            session.abort(exc)
        }

        log.info "nf-lamin> Initializing nf-lamin plugin with config: ${config}"
    }

}
