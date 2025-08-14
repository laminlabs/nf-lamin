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
package nextflow.lamin.instance

import spock.lang.Specification
import spock.lang.Unroll

import nextflow.lamin.instance.InstanceSettings

/**
 * Unit tests for the InstanceSettings class
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
class InstanceSettingsTest extends Specification {

    def "should create InstanceSettings from valid map"() {
        given:
        def map = [
            id: "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            owner: "testowner",
            name: "testname",
            schema_id: "f47ac10b-58cc-4372-a567-0e02b2c3d480",
            api_url: "https://api.example.com"
        ]

        when:
        def settings = InstanceSettings.fromMap(map)

        then:
        settings.id.toString() == "f47ac10b-58cc-4372-a567-0e02b2c3d479"
        settings.owner == "testowner"
        settings.name == "testname"
        settings.schemaId.toString() == "f47ac10b-58cc-4372-a567-0e02b2c3d480"
        settings.apiUrl == "https://api.example.com"
    }

    @Unroll
    def "should throw IllegalStateException for missing field '#missingField'"() {
        given:
        def baseMap = [
            id: "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            owner: "testowner",
            name: "testname",
            schema_id: "f47ac10b-58cc-4372-a567-0e02b2c3d480",
            api_url: "https://api.example.com"
        ]
        baseMap.remove(missingField)

        when:
        InstanceSettings.fromMap(baseMap)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains(expectedMessage)

        where:
        missingField  | expectedMessage
        'id'          | 'id is empty'
        'owner'       | 'owner is empty'
        'name'        | 'name is empty'
        'schema_id'   | 'schema_id is empty'
        'api_url'     | 'api_url is empty'
    }

    def "should throw IllegalStateException for null map"() {
        when:
        InstanceSettings.fromMap(null)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Instance settings map is empty.'
    }

    def "should throw IllegalStateException for empty map"() {
        when:
        InstanceSettings.fromMap([:])

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Instance settings map is empty.'
    }

    def "should handle invalid UUID format"() {
        given:
        def map = [
            id: "invalid-uuid",
            owner: "testowner",
            name: "testname",
            schema_id: "f47ac10b-58cc-4372-a567-0e02b2c3d480",
            api_url: "https://api.example.com"
        ]

        when:
        InstanceSettings.fromMap(map)

        then:
        thrown(IllegalArgumentException)
    }

    def "should create toString representation"() {
        given:
        def map = [
            id: "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            owner: "testowner",
            name: "testname",
            schema_id: "f47ac10b-58cc-4372-a567-0e02b2c3d480",
            api_url: "https://api.example.com"
        ]
        def settings = InstanceSettings.fromMap(map)

        when:
        def result = settings.toString()

        then:
        result.contains("InstanceSettings")
        result.contains("testowner")
        result.contains("testname")
        result.contains("f47ac10b-58cc-4372-a567-0e02b2c3d479")
    }
}
