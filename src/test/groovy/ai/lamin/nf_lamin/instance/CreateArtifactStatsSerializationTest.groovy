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

import ai.lamin.lamin_api_client.model.CreateArtifactRequestBody
import ai.lamin.nf_lamin.model.ArtifactStats
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Pins how the generated client serialises the `stats` kwarg, since lamindb reads it as JSON.
 */
class CreateArtifactStatsSerializationTest extends Specification {

    def 'stats are sent as a nested object with integer sizes'() {
        given:
        CreateArtifactRequestBody body = new CreateArtifactRequestBody(path: 's3://bucket/dir')
        body.putKwargsItem('_stats', new ArtifactStats(3L, 'YNCwyFw7rBegqZLBVEckCg', 'md5-d', 2L).toKwargs())
        body.putKwargsItem('skip_check_exists', true)

        when:
        String json = body.toJson()
        Map parsed = new JsonSlurper().parseText(json) as Map

        then:
        json.contains('"size":3')
        json.contains('"n_files":2')
        parsed.kwargs._stats == [size: 3, hash: 'YNCwyFw7rBegqZLBVEckCg', hash_type: 'md5-d', n_files: 2]
        parsed.kwargs.skip_check_exists == true
    }

    def 'a null n_files is omitted from the wire, so the server must read it with .get()'() {
        given:
        CreateArtifactRequestBody body = new CreateArtifactRequestBody(path: 's3://bucket/file.txt')
        body.putKwargsItem('_stats', new ArtifactStats(3L, 'kAFQmDzST7DWlj99KOF_cg', 'md5', null).toKwargs())

        when:
        Map parsed = new JsonSlurper().parseText(body.toJson()) as Map

        then:
        parsed.kwargs._stats.keySet() == ['size', 'hash', 'hash_type'] as Set
    }
}
