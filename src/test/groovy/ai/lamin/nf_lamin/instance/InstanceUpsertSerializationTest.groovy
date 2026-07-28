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

import ai.lamin.lamin_api_client.JSON
import ai.lamin.lamin_api_client.model.Body
import spock.lang.Specification

/**
 * Guards the client serialization behaviour {@link Instance#upsertRecord} relies on:
 * nullable conflict columns (e.g. {@code feature_id} in link-table upserts) must be
 * sent as explicit JSON nulls. Requires lamin-api-client >= 0.1.0.
 */
class InstanceUpsertSerializationTest extends Specification {

    def "Body keeps explicit null values in upsert records"() {
        given:
        Body body = new Body([[artifact_id: 1, project_id: 2, feature_id: null]])

        when:
        String json = JSON.getGson().toJson(body)

        then:
        json.contains('"feature_id":null')
        json.contains('"artifact_id":1')
        json.contains('"project_id":2')
    }
}
