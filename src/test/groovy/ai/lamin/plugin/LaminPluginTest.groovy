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

import spock.lang.Specification
import nextflow.Session
import org.pf4j.PluginWrapper

/**
 * Unit tests for the LaminPlugin class
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
class LaminPluginTest extends Specification {

    def setup() {
        // Clear static state before each test
        LaminPlugin.@_session = null
        LaminPlugin.@_config = null
    }

    def cleanup() {
        // Clear static state after each test
        LaminPlugin.@_session = null
        LaminPlugin.@_config = null
    }

    def "should create LaminPlugin with wrapper"() {
        given:
        def wrapper = Mock(PluginWrapper)

        when:
        def plugin = new LaminPlugin(wrapper)

        then:
        plugin != null
    }

    def "should set and get session correctly"() {
        given:
        def session = Mock(Session)

        when:
        LaminPlugin.setSession(session)
        def retrievedSession = LaminPlugin.getSession()

        then:
        retrievedSession == session
    }

    def "should throw IllegalArgumentException for null session"() {
        when:
        LaminPlugin.setSession(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Session cannot be null'
    }

    def "should throw IllegalStateException when getting session without setting it"() {
        when:
        LaminPlugin.getSession()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('LaminPlugin requires a valid Nextflow session')
    }

    def "should throw IllegalStateException when setting different session"() {
        given:
        def session1 = Mock(Session)
        def session2 = Mock(Session)

        when:
        LaminPlugin.setSession(session1)
        LaminPlugin.setSession(session2)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Session already set to a different instance'
    }

    def "should allow setting the same session multiple times"() {
        given:
        def session = Mock(Session)

        when:
        LaminPlugin.setSession(session)
        LaminPlugin.setSession(session)

        then:
        noExceptionThrown()
        LaminPlugin.getSession() == session
    }

    def "should get config from session"() {
        given:
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'testowner/testinstance',
                    api_key: 'test-api-key'
                ]
            ]
        }

        when:
        LaminPlugin.setSession(session)
        def config = LaminPlugin.getConfig()

        then:
        config != null
        config.getInstance() == 'testowner/testinstance'
        config.getApiKey() == 'test-api-key'
    }

    def "should cache config after first access"() {
        given:
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'testowner/testinstance',
                    api_key: 'test-api-key'
                ]
            ]
        }

        when:
        LaminPlugin.setSession(session)
        def config1 = LaminPlugin.getConfig()
        def config2 = LaminPlugin.getConfig()

        then:
        config1 == config2
    }
}
