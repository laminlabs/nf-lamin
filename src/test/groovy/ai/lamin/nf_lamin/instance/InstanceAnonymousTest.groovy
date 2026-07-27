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

package ai.lamin.nf_lamin.instance

import ai.lamin.nf_lamin.hub.InstanceSettings
import ai.lamin.nf_lamin.hub.LaminHub
import spock.lang.Specification

/**
 * Tests that an anonymous hub causes instance API requests to be sent without an
 * Authorization header. The instance API treats a missing header as anonymous and
 * serves public instances, whereas the publishable anon key is rejected there.
 */
class InstanceAnonymousTest extends Specification {

    private static InstanceSettings fakeSettings() {
        return new InstanceSettings([
            id       : '037ba1e0-8d80-4f91-a902-75a47735076a',
            owner    : 'laminlabs',
            name     : 'lamindata',
            schema_id: '097186c8-e529-4bf4-9d5c-cf9d1f5a2ed3',
            api_url  : 'https://aws.us-east-1.lamin.ai/api',
        ] as Map<String, Object>)
    }

    def "anonymous hub yields a null bearer token (no Authorization header)"() {
        given:
        def hub = new LaminHub('https://hub.lamin.ai', 'sb_publishable_test', null)
        def instance = new Instance(hub, fakeSettings(), 3, 1000, 30000)

        expect:
        hub.anonymous
        instance.getBearerToken() == null
    }

    def "authenticated hub yields a Bearer token"() {
        given:
        // A pre-set access token avoids a network JWT fetch.
        def hub = new LaminHub('https://hub.lamin.ai', 'sb_publishable_test', 'user-api-key')
        hub.@accessToken = 'preset-jwt'
        def instance = new Instance(hub, fakeSettings(), 3, 1000, 30000)

        expect:
        !hub.anonymous
        instance.getBearerToken() == 'Bearer preset-jwt'
    }
}
