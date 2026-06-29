/*
 * Copyright (c) 2013-2024, Lamin Labs GmbH.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package ai.lamin.nf_lamin.instance

import ai.lamin.lamin_api_client.ApiException
import spock.lang.Specification

/**
 * Tests detection of a backend FileNotFoundError and the actionable
 * "no access to this bucket via Lamin Hub" message built from it.
 */
class InstanceStorageAccessErrorTest extends Specification {

    private static ApiException apiError(int code, String body) {
        return new ApiException(code, 'boom', null, body)
    }

    def "isFileNotFoundError only matches FileNotFoundError bodies"() {
        expect:
        Instance.isFileNotFoundError(apiError(500, body)) == expected

        where:
        body                                                       | expected
        '{"detail":"... -- FileNotFoundError: s3://bucket/key"}'    | true
        '{"detail":"... UnknownStorageLocation ..."}'              | false
        '{"detail":"... BlobHashNotFound ..."}'                   | false
        null                                                       | false
    }

    def "fromResponseBody parses the offending bucket and path"() {
        given:
        def body = '{"detail":"Lambda execution error for Artifact:create. Artifact creation failed -- ' +
            'FileNotFoundError: s3://nf-lamin-test/scratch-staging/stress/run_20260629_140337/artifact_x.bin"}'

        when:
        def ex = StorageAccessException.fromResponseBody(body, new RuntimeException('cause'))

        then:
        ex.bucket == 'nf-lamin-test'
        ex.path == 's3://nf-lamin-test/scratch-staging/stress/run_20260629_140337/artifact_x.bin'
        // the extracted path must not bleed the JSON terminator
        !ex.path.contains('"}')
        ex.cause instanceof RuntimeException
    }

    def "describe names the given user, bucket and path"() {
        given:
        def ex = StorageAccessException.fromResponseBody(
            '{"detail":"FileNotFoundError: s3://nf-lamin-test/key.bin"}', null)

        when:
        def msg = ex.describe('rcannood')

        then:
        msg.contains("user 'rcannood'")
        msg.contains("storage bucket 'nf-lamin-test'")
        msg.contains('s3://nf-lamin-test/key.bin')
        msg.contains('Lamin Hub')
    }

    def "describe degrades gracefully without a handle or parseable path"() {
        given:
        def ex = StorageAccessException.fromResponseBody('{"detail":"FileNotFoundError"}', null)

        expect:
        ex.bucket == null
        ex.path == null
        // the default (handle-less) message still reads sensibly
        ex.message.contains('the current user')
        ex.message.contains('this storage bucket')
        ex.message.contains('Lamin Hub')
        ex.describe(null) == ex.message
    }
}
