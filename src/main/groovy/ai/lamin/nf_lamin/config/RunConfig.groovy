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
 * Configuration for run-specific metadata linking.
 *
 * This configuration allows specifying project and ulabel UIDs that will be
 * linked to the run when it is created.
 *
 * Example usage in nextflow.config:
 * <pre>
 * lamin {
 *   run {
 *     project_uids = ['proj123456789012']
 *     ulabel_uids = ['ulab123456789012']
 *   }
 * }
 * </pre>
 */
@CompileStatic
class RunConfig {

    @ConfigOption
    @Description('''
        List of project UIDs to link to the run.
    ''')
    final List<String> projectUids

    @ConfigOption
    @Description('''
        List of ulabel UIDs to link to the run.
    ''')
    final List<String> ulabelUids

    /**
     * Default constructor required for extension point
     */
    RunConfig() {
        this.projectUids = []
        this.ulabelUids = []
    }

    /**
     * Create a RunConfig from a configuration map.
     *
     * @param opts Configuration map with keys: project_uids, ulabel_uids
     */
    RunConfig(Map opts) {
        this.projectUids = parseUidList(opts?.project_uids)
        this.ulabelUids = parseUidList(opts?.ulabel_uids)
    }

    /**
     * Parse a UID list from various input types.
     *
     * @param value The input value (can be null, String, or List)
     * @return A list of UIDs
     */
    private static List<String> parseUidList(Object value) {
        if (value == null) {
            return []
        }
        if (value instanceof List) {
            return value.collect { it?.toString() }.findAll { it }
        }
        if (value instanceof String) {
            return [value]
        }
        return []
    }

    /**
     * Get the list of project UIDs to link.
     * @return List of project UIDs
     */
    List<String> getProjectUids() {
        return this.projectUids
    }

    /**
     * Get the list of ulabel UIDs to link.
     * @return List of ulabel UIDs
     */
    List<String> getUlabelUids() {
        return this.ulabelUids
    }

    @Override
    String toString() {
        return "RunConfig{projectUids=${projectUids}, ulabelUids=${ulabelUids}}"
    }
}
