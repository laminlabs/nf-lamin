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

import spock.lang.Specification
import org.pf4j.PluginWrapper

/**
 * Unit tests for the LaminPlugin class
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
class LaminPluginTest extends Specification {

    def "should create LaminPlugin with wrapper"() {
        given:
        def wrapper = Mock(PluginWrapper)

        when:
        def plugin = new LaminPlugin(wrapper)

        then:
        plugin != null
    }
}
