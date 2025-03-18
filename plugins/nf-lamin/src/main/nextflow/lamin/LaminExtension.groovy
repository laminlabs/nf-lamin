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
    private Session session

    /*
     * A Custom config extracted from nextflow.config under lamin tag
     * nextflow.config
     * ---------------
     * docker{
     *   enabled = true
     * }
     * ...
     * lamin{
     *    prefix = 'Mrs'
     * }
     */
     private LaminConfig config

    /*
     * nf-core initializes the plugin once loaded and session is ready
     * @param session
     */
    @Override
    protected void init(Session session) {
        this.session = session
        this.config = new LaminConfig(session.config.navigate('lamin') as Map)
    }

    /*
     * {@code reverse} is a `producer` method and will be available to the script because:
     *
     * - it's public
     * - it returns a DataflowWriteChannel
     * - it's marked with the @Factory annotation
     *
     * The method can require arguments but it's not mandatory, it depends of the business logic of the method.
     *
     */
    @Factory
    DataflowWriteChannel reverse(String message) {
        final channel = CH.create()
        session.addIgniter((action) -> reverseImpl(channel, message))
        return channel
    }

    private void reverseImpl(DataflowWriteChannel channel, String message) {
        channel.bind(message.reverse());
        channel.bind(Channel.STOP)
    }

    /*
    * {@code goodbye} is a *consumer* method as it receives values from a channel to perform some logic.
    *
    * Consumer methods are introspected by nextflow-core and include into the DSL if the method:
    *
    * - it's public
    * - it returns a DataflowWriteChannel
    * - it has only one arguments of DataflowReadChannel class
    * - it's marked with the @Operator annotation 
    *
    * a consumer method needs to proportionate 2 closures:
    * - a closure to consume items (one by one)
    * - a finalizer closure
    *
    * in this case `goodbye` will consume a message and will store it as an upper case
    */
    @Operator
    DataflowWriteChannel goodbye(DataflowReadChannel source) {
        final target = CH.createBy(source)
        final next = { target.bind("Goodbye $it".toString()) }
        final done = { target.bind(Channel.STOP) }
        DataflowHelper.subscribeImpl(source, [onNext: next, onComplete: done])
        return target
    }

    /*
     * Generate a random string
     *
     * Using @Function annotation we allow this function can be imported from the pipeline script
     */
    @Function
    String randomString(int length=9){
        new Random().with {(1..length).collect {(('a'..'z')).join(null)[ nextInt((('a'..'z')).join(null).length())]}.join(null)}
    }

}
