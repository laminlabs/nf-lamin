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

import groovy.transform.CompileStatic
import nextflow.config.schema.ConfigOption
import nextflow.script.dsl.Description

/**
 * Feature flags for the Lamin plugin.
 *
 * Allows enabling or disabling optional plugin features.
 *
 * Example usage in nextflow.config:
 * <pre>
 * lamin {
 *   features {
 *     manage_s3_credentials = false   // disable if credential federation causes issues
 *   }
 * }
 * </pre>
 */
@CompileStatic
class FeaturesConfig {

    @ConfigOption
    @Description('''
        Enable automatic credential federation for Lamin-managed S3 storage.
        When enabled (default: true), the plugin fetches temporary STS credentials
        from LaminHub and uses them to access private S3 buckets without requiring
        the user to configure AWS credentials. Disable this if credential federation
        causes issues in your environment.
    ''')
    final Boolean manageS3Credentials

    /**
     * Default constructor
     */
    FeaturesConfig() {
        this.manageS3Credentials = true
    }

    /**
     * Create a FeaturesConfig from a configuration map.
     *
     * @param opts Configuration map with keys: manage_s3_credentials
     */
    FeaturesConfig(Map opts) {
        this.manageS3Credentials = opts?.containsKey('manage_s3_credentials')
            ? (opts.manage_s3_credentials as Boolean)
            : true
    }

    @Override
    String toString() {
        return "FeaturesConfig{manageS3Credentials=${manageS3Credentials}}"
    }
}
