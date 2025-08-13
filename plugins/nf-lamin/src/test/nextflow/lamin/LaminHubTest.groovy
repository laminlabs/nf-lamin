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

import spock.lang.Specification
import spock.lang.Unroll

import nextflow.lamin.hub.LaminHub

/**
 * Test laminhub class
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
class LaminHubTest extends Specification {

    def "should create LaminHub with valid parameters"() {
        when:
        def hub = new LaminHub(
            "https://api.example.com",
            "test-anon-key",
            "test-api-key"
        )

        then:
        hub != null
    }

    @Unroll
    def "should throw IllegalArgumentException for invalid '#paramName'"() {
        when:
        new LaminHub(apiUrl, anonKey, apiKey)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(expectedMessage)

        where:
        paramName   | apiUrl                  | anonKey        | apiKey         | expectedMessage
        'apiUrl'    | null                    | "anon-key"     | "api-key"      | "API URL cannot be null or empty"
        'apiUrl'    | ""                      | "anon-key"     | "api-key"      | "API URL cannot be null or empty"
        'apiUrl'    | "  "                    | "anon-key"     | "api-key"      | "API URL cannot be null or empty"
        'anonKey'   | "https://api.test.com"  | null           | "api-key"      | "Anonymous Key cannot be null or empty"
        'anonKey'   | "https://api.test.com"  | ""             | "api-key"      | "Anonymous Key cannot be null or empty"
        'anonKey'   | "https://api.test.com"  | "  "           | "api-key"      | "Anonymous Key cannot be null or empty"
        'apiKey'    | "https://api.test.com"  | "anon-key"     | null           | "API Key cannot be null or empty"
        'apiKey'    | "https://api.test.com"  | "anon-key"     | ""             | "API Key cannot be null or empty"
        'apiKey'    | "https://api.test.com"  | "anon-key"     | "  "           | "API Key cannot be null or empty"
    }

    def "should handle proper initialization"() {
        given:
        def apiUrl = "https://api.example.com"
        def anonKey = "test-anon-key"
        def apiKey = "test-api-key"

        when:
        def hub = new LaminHub(apiUrl, anonKey, apiKey)

        then:
        hub != null
        // Note: We can't easily test the private fields without reflection
        // or additional getters, but we can verify the object is created
    }

    def "should handle URL validation"() {
        when:
        def hub = new LaminHub(
            "https://valid.url.com",
            "valid-anon-key",
            "valid-api-key"
        )

        then:
        hub != null
    }
}
