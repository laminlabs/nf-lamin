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

import ai.lamin.nf_lamin.LaminConfig
import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.hub.LaminHubSettings
import ai.lamin.nf_lamin.hub.InstanceSettings
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for the batch record endpoints {@link Instance#batchUpdateRecords} and
 * {@link Instance#batchDeleteRecords}.
 *
 * <p>Argument-validation tests run without credentials (the validation happens before
 * any network call). The round-trip test is gated behind {@code LAMIN_API_KEY} and
 * exercises create -> batch-update -> batch-delete against {@code laminlabs/lamindata}.
 */
class InstanceBatchRecordsTest extends Specification {

    @Shared String apiKey = System.getenv('LAMIN_API_KEY')
    @Shared Instance instance

    // Unique suffix per test run to avoid collisions with prior/parallel runs
    @Shared String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8)

    // Track created ulabel uids for cleanup on failure
    @Shared List<String> createdUlabelUids = []

    def setupSpec() {
        if (apiKey) {
            def config = LaminConfig.parseConfig([
                instance: 'laminlabs/lamindata',
                api_key: apiKey,
            ])
            def resolvedConfig = LaminHubSettings.resolve(config)
            def hub = new LaminHub(
                resolvedConfig.supabaseApiUrl,
                resolvedConfig.supabaseAnonKey,
                config.apiKey
            )
            def settings = hub.getInstanceSettings(
                config.instanceOwner,
                config.instanceName
            )
            instance = new Instance(hub, settings, 3, 1000, 30000)
        }
    }

    def cleanupSpec() {
        // Best-effort cleanup of any ulabels left behind by a failed round-trip
        createdUlabelUids.findAll { it }.unique().each { uid ->
            try {
                instance.deleteRecord(moduleName: 'core', modelName: 'ulabel', uid: uid)
            } catch (Exception e) {
                println "WARNING: could not clean up ulabel ${uid}: ${e.message}"
            }
        }
    }

    // -------------------------------------------------------------------
    // Argument validation (no network required)
    // -------------------------------------------------------------------

    /** Builds an Instance with dummy settings; no network call is made by the constructor. */
    private static Instance noNetworkInstance() {
        def hub = new LaminHub('https://api.example.com', 'anon-key', 'api-key')
        def settings = new InstanceSettings([
            id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
            owner: 'testowner',
            name: 'testname',
            schema_id: 'f47ac10b-58cc-4372-a567-0e02b2c3d480',
            api_url: 'https://api.example.com',
        ])
        return new Instance(hub, settings, 3, 100, 30000)
    }

    @Unroll
    def "batchUpdateRecords throws when '#missing' is missing"() {
        given:
        def inst = noNetworkInstance()
        def args = [
            moduleName: 'core',
            modelName: 'artifact',
            indexColumns: ['id'],
            records: [[id: 1, description: 'x']],
        ]
        args.remove(missing)

        when:
        inst.batchUpdateRecords(args)

        then:
        thrown(IllegalStateException)

        where:
        missing << ['moduleName', 'modelName', 'indexColumns', 'records']
    }

    @Unroll
    def "batchDeleteRecords throws when '#missing' is missing"() {
        given:
        def inst = noNetworkInstance()
        def args = [
            moduleName: 'core',
            modelName: 'artifactproject',
            records: [[artifact_id: 1, project_id: 2]],
        ]
        args.remove(missing)

        when:
        inst.batchDeleteRecords(args)

        then:
        thrown(IllegalStateException)

        where:
        missing << ['moduleName', 'modelName', 'records']
    }

    def "batchDeleteRecords throws on empty records list"() {
        given:
        def inst = noNetworkInstance()

        when:
        inst.batchDeleteRecords(moduleName: 'core', modelName: 'ulabel', records: [])

        then:
        thrown(IllegalStateException)
    }

    // -------------------------------------------------------------------
    // Live round-trip: create -> batch-update -> batch-delete
    // -------------------------------------------------------------------

    @IgnoreIf({ !env.LAMIN_API_KEY })
    def "batchUpdateRecords then batchDeleteRecords round-trip on core.ulabel"() {
        given: "two freshly created ulabels"
        String name1 = "nf-lamin-batch-test-${uniqueSuffix}-1"
        String name2 = "nf-lamin-batch-test-${uniqueSuffix}-2"
        Map u1 = instance.createRecord(moduleName: 'core', modelName: 'ulabel', data: [name: name1])
        Map u2 = instance.createRecord(moduleName: 'core', modelName: 'ulabel', data: [name: name2])
        Integer id1 = (u1.id as Number).intValue()
        Integer id2 = (u2.id as Number).intValue()
        String uid1 = u1.uid as String
        String uid2 = u2.uid as String
        createdUlabelUids.addAll([uid1, uid2])
        assert id1 != null && id2 != null : "ulabel creation did not return ids: ${u1}, ${u2}"

        when: "batch-updating their descriptions by id"
        String newDescription = "updated-${uniqueSuffix}"
        List<Map> updated = instance.batchUpdateRecords(
            moduleName: 'core',
            modelName: 'ulabel',
            indexColumns: ['id'],
            records: [
                [id: id1, description: newDescription],
                [id: id2, description: newDescription],
            ]
        )

        then: "both records are returned"
        updated != null
        updated.size() == 2

        and: "the update is persisted (verified via a fresh fetch)"
        Map refetched = instance.getRecord(moduleName: 'core', modelName: 'ulabel', idOrUid: uid1)
        refetched.description == newDescription

        when: "batch-deleting both records by id"
        Integer deletedCount = instance.batchDeleteRecords(
            moduleName: 'core',
            modelName: 'ulabel',
            records: [[id: id1], [id: id2]]
        )

        then: "the reported deleted count matches"
        deletedCount == 2

        and: "the records can no longer be found"
        instance.getRecords(moduleName: 'core', modelName: 'ulabel', filter: [name: [eq: name1]]).isEmpty()
        instance.getRecords(moduleName: 'core', modelName: 'ulabel', filter: [name: [eq: name2]]).isEmpty()

        cleanup: "already deleted; clear tracking so cleanupSpec does not retry"
        createdUlabelUids.removeAll([uid1, uid2])
    }
}
