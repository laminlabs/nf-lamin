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

package ai.lamin.nf_lamin.nio

import spock.lang.Specification

import ai.lamin.nf_lamin.hub.InstanceSettings
import ai.lamin.nf_lamin.instance.Instance

class LaminStorageResolverTest extends Specification {

    static final String INSTANCE_UID = 'inst12345678'

    LaminStorageResolver resolver
    Instance instance

    def setup() {
        resolver = new LaminStorageResolver()
        instance = Mock(Instance)
        instance.getSettings() >> settings()
    }

    private static InstanceSettings settings() {
        return new InstanceSettings([
            id: '037ba1e0-8d80-4f91-a902-75a47735076a',
            owner: 'laminlabs',
            name: 'lamindata',
            schema_id: '90541d56-0ee5-4757-b93a-8afa8ace1bd1',
            api_url: 'https://api.example.org',
            lnid: INSTANCE_UID,
            storage: [
                root: 's3://lamin-us-east-1/JwMEKs04D9WJ',
                lnid: 'dflt12345678',
                type: 's3',
                region: 'us-east-1'
            ]
        ] as Map<String, Object>)
    }

    private static LaminUriParser uri(String value) {
        return LaminUriParser.parse(value)
    }

    def "should use the instance default storage when nothing is selected"() {
        when:
        def target = resolver.resolve(instance, uri('lamin://laminlabs/lamindata?prefix=results'))

        then: 'no API call is needed'
        0 * instance.getRecord(_)
        0 * instance.getRecords(_)

        and:
        target.storageRoot == 's3://lamin-us-east-1/JwMEKs04D9WJ'
        target.storageUid == 'dflt12345678'
        target.region == 'us-east-1'
        target.spaceId == null
    }

    def "should look up a storage by uid"() {
        given:
        instance.getRecord({ Map a -> a.modelName == 'storage' && a.idOrUid == 'stor12345678' }) >> [
            id: 3, uid: 'stor12345678', root: 's3://other-bucket/root', type: 's3',
            region: 'eu-west-1', instance_uid: INSTANCE_UID, space_id: 1
        ]

        when:
        def target = resolver.resolve(instance, uri('lamin://laminlabs/lamindata/storage/stor12345678'))

        then:
        target.storageRoot == 's3://other-bucket/root'
        target.storageId == 3
        target.region == 'eu-west-1'
        target.spaceId == 1
    }

    def "should pick the lowest-id storage of a space"() {
        given:
        instance.getRecord({ Map a -> a.modelName == 'space' }) >> [id: 7, uid: 'spce12345678', name: 'dev']
        instance.getRecords({ Map a -> a.modelName == 'storage' }) >> [
            [id: 4, uid: 'stor12345678', root: 's3://dev-bucket/root', type: 's3', instance_uid: INSTANCE_UID, space_id: 7]
        ]

        when:
        def target = resolver.resolve(instance, uri('lamin://laminlabs/lamindata/space/spce12345678'))

        then:
        target.storageRoot == 's3://dev-bucket/root'
        target.spaceId == 7
        target.spaceUid == 'spce12345678'
    }

    def "should fail when a space has no storage location"() {
        given:
        instance.getRecord({ Map a -> a.modelName == 'space' }) >> [id: 7, uid: 'spce12345678']
        instance.getRecords(_) >> []

        when:
        resolver.resolve(instance, uri('lamin://laminlabs/lamindata/space/spce12345678'))

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('No storage location found for space')
    }

    def "should fail when the space does not exist"() {
        given:
        instance.getRecord({ Map a -> a.modelName == 'space' }) >> null

        when:
        resolver.resolve(instance, uri('lamin://laminlabs/lamindata/space/nope12345678'))

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("No space with UID 'nope12345678'")
    }

    def "should fail when the storage does not exist"() {
        given:
        instance.getRecord({ Map a -> a.modelName == 'storage' }) >> null

        when:
        resolver.resolve(instance, uri('lamin://laminlabs/lamindata/storage/nope12345678'))

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("No storage with UID 'nope12345678'")
    }

    def "should fail when the storage is managed by another instance"() {
        given:
        instance.getRecord({ Map a -> a.modelName == 'storage' }) >> [
            id: 3, uid: 'stor12345678', root: 's3://foreign/root', instance_uid: 'other1234567'
        ]

        when:
        resolver.resolve(instance, uri('lamin://laminlabs/lamindata/storage/stor12345678'))

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('read-only in instance')
    }

    def "should fail when the selected space and storage disagree"() {
        given:
        instance.getRecord({ Map a -> a.modelName == 'space' }) >> [id: 7, uid: 'spce12345678']
        instance.getRecord({ Map a -> a.modelName == 'storage' }) >> [
            id: 3, uid: 'stor12345678', root: 's3://bucket/root', instance_uid: INSTANCE_UID, space_id: 9
        ]

        when:
        resolver.resolve(instance, uri('lamin://laminlabs/lamindata/space/spce12345678?storage=stor12345678'))

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('LaminDB requires them to match')
    }

    def "should resolve a target only once"() {
        when:
        3.times { resolver.resolve(instance, uri("lamin://laminlabs/lamindata/storage/stor12345678?prefix=run${it}")) }

        then:
        1 * instance.getRecord(_) >> [id: 3, uid: 'stor12345678', root: 's3://bucket/root', instance_uid: INSTANCE_UID]
    }

    def "should reject artifact URIs"() {
        when:
        resolver.resolve(instance, uri('lamin://laminlabs/lamindata/artifact/uid1234567890ab'))

        then:
        thrown(IllegalArgumentException)
    }

    def "resolveUri should join the root and key"() {
        given:
        def target = new LaminStorageTarget(storageRoot: 's3://bucket/root')

        expect:
        target.resolveUri('a/b.txt') == 's3://bucket/root/a/b.txt'
        target.resolveUri(null) == 's3://bucket/root'
        new LaminStorageTarget(storageRoot: 's3://bucket/root/').resolveUri('a.txt') == 's3://bucket/root/a.txt'
    }
}
