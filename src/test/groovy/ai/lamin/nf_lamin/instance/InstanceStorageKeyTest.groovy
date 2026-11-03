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
import spock.lang.Unroll

/**
 * Tests how the storage key of an artifact is derived from its record.
 */
class InstanceStorageKeyTest extends Specification {

    private static final String UID = '1ASdDl3pNVdUMPx50000'

    private Instance instanceReturning(Map artifact) {
        def settings = new InstanceSettings([
            id       : UUID.randomUUID().toString(),
            owner    : 'laminlabs',
            name     : 'lamindata',
            schema_id: UUID.randomUUID().toString(),
            api_url  : 'http://localhost:1',
        ])
        Instance instance = Spy(new Instance(Stub(LaminHub), settings, 0, 1, 1))
        instance.getRecords(_) >> [artifact]
        instance.getStorage(_) >> [root: 's3://bucket']
        return instance
    }

    @Unroll
    def "a virtual key with suffix '#suffix' and overwrite_versions=#overwrite resolves to #expected"() {
        given:
        def instance = instanceReturning([
            uid: UID, suffix: suffix, storage_id: 1, key: null,
            _key_is_virtual: true, _overwrite_versions: overwrite,
        ])

        expect:
        instance.getArtifactStorageInfo(UID).artifactKey == expected

        where:
        suffix  | overwrite | expected
        ''      | false     | ".lamindb/${UID}"
        ''      | true      | ".lamindb/${UID.substring(0, 16)}"
        '.zarr' | false     | ".lamindb/${UID}.zarr"
        '.zarr' | true      | ".lamindb/${UID.substring(0, 16)}.zarr"
    }

    def "a virtual key without a suffix field is rejected"() {
        given:
        def instance = instanceReturning([
            uid: UID, suffix: null, storage_id: 1, key: null, _key_is_virtual: true,
        ])

        when:
        instance.getArtifactStorageInfo(UID)

        then:
        thrown(AssertionError)
    }
}
