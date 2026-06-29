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

import ai.lamin.lamin_api_client.ApiException
import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.hub.InstanceSettings
import spock.lang.Specification

/**
 * Tests that the API client turns a 403 (and the transitional 5xx FileNotFoundError)
 * into a PermissionDeniedException naming the operation + data, and that the message
 * reads sensibly once the caller supplies the connected user handle.
 */
class InstancePermissionDeniedTest extends Specification {

    private Instance newInstance() {
        def hub = Stub(LaminHub)
        def settings = new InstanceSettings([
            id       : UUID.randomUUID().toString(),
            owner    : 'laminlabs',
            name     : 'lamindata',
            schema_id: UUID.randomUUID().toString(),
            api_url  : 'http://localhost:1',
        ])
        // no retries, so the retry branch never sleeps during these tests
        return new Instance(hub, settings, 0, 1, 1)
    }

    private static ApiException apiError(int code, String body) {
        return new ApiException(code, 'boom', null, body)
    }

    def "callApi maps any 403 to a PermissionDeniedException carrying the operation and data"() {
        given:
        def instance = newInstance()

        when:
        instance.callApi('POST createArtifact', 's3://bucket/key.bin') {
            throw apiError(403, body)
        }

        then:
        PermissionDeniedException ex = thrown()
        ex.operation == 'POST createArtifact'
        ex.data == 's3://bucket/key.bin'

        where:
        // a 403 is treated as permission-denied regardless of the body shape
        body << [
            '{"detail":"... -- NoWriteAccess: You’re not allowed to write to the instance."}',
            '{"detail":"... -- PermissionError: permission denied"}',
            '{"detail":"... -- ClientError: An error occurred (AccessDenied) when calling HeadObject"}',
        ]
    }

    def "callApi maps a transitional 5xx permission error to a PermissionDeniedException"() {
        given:
        def instance = newInstance()

        when:
        instance.callApi('POST createArtifact', 'data=z') {
            throw apiError(500, body)
        }

        then:
        PermissionDeniedException ex = thrown()
        ex.operation == 'POST createArtifact'

        where:
        // observed current 500 bodies (pre-laminhub#5363)
        body << [
            '{"detail":"Lambda execution error for Artifact:create. Artifact creation failed -- PermissionError: Forbidden"}',
            '{"detail":"... -- FileNotFoundError: s3://bucket/key.bin"}',
        ]
    }

    def "callApi rethrows other 5xx errors unchanged (no permission message)"() {
        given:
        def instance = newInstance()

        when:
        instance.callApi('POST createArtifact', 'data=z') {
            throw apiError(502, '{"detail":"Bad Gateway"}')
        }

        then:
        ApiException ex = thrown()
        ex.code == 502
    }

    def "describe names the user, operation and data"() {
        given:
        def ex = new PermissionDeniedException('POST createArtifact', 's3://bucket/key.bin', null)

        when:
        def msg = ex.describe('rcannood')

        then:
        msg.contains("user 'rcannood'")
        msg.contains('POST createArtifact')
        msg.contains('s3://bucket/key.bin')
        msg.contains('Lamin Hub')
    }

    def "describe degrades gracefully without a handle, operation or data"() {
        given:
        def ex = new PermissionDeniedException(null, null, null)

        expect:
        ex.message.contains('the current user')
        ex.message.contains('this operation')
        ex.describe(null) == ex.message
    }
}
