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
package ai.lamin.plugin

import nextflow.Session
import spock.lang.Specification

/**
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
class LaminObserverFactoryTest extends Specification {

    def 'should return observer' () {
        when:
        def result = new LaminFactory().create(Mock(Session))
        then:
        result.size() == 1
        result[0] instanceof LaminObserver
    }

}
