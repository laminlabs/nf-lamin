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

package ai.lamin.nf_lamin.config

import spock.lang.Specification

class FeaturesConfigTest extends Specification {

    def "should enable every feature by default"() {
        given:
        def features = new FeaturesConfig()

        expect:
        features.manage_s3_credentials
        features.use_output_labels
        features.publish_to_lamin_storage
    }

    def "should default features that are not in the config map"() {
        given:
        def features = new FeaturesConfig([manage_s3_credentials: false])

        expect:
        !features.manage_s3_credentials
        features.use_output_labels
        features.publish_to_lamin_storage
    }

    def "should read publish_to_lamin_storage from the config map"() {
        expect:
        !new FeaturesConfig([publish_to_lamin_storage: false]).publish_to_lamin_storage
        new FeaturesConfig([publish_to_lamin_storage: true]).publish_to_lamin_storage
    }

    def "should handle a null config map"() {
        given:
        def features = new FeaturesConfig(null)

        expect:
        features.manage_s3_credentials
        features.publish_to_lamin_storage
    }
}
