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

package ai.lamin.nf_lamin

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.config.spec.ConfigOption
import nextflow.config.spec.ConfigScope
import nextflow.config.spec.ScopeName
import nextflow.script.dsl.Description

import ai.lamin.nf_lamin.config.ArtifactConfig
import ai.lamin.nf_lamin.config.ApiConfig
import ai.lamin.nf_lamin.config.FeaturesConfig
import ai.lamin.nf_lamin.config.RunConfig
import ai.lamin.nf_lamin.config.TransformConfig

/**
 * Handle the configuration of the Lamin plugin
 *
 * These settings can be defined in the nextflow config as follows:
 *
 * lamin {
 *   instance = 'laminlabs/lamindata'
 *   api_key = System.getenv('LAMIN_API_KEY')
 *   project_uids = ['proj123456789012']
 *   ulabel_uids = ['ulab123456789012']
 *   space_uid = 'spce123456789012'
 *   branch_uid = 'brch123456789012'
 *   env = 'prod'
 *   dry_run = false
 *   run {
 *     ulabel_uids = ['ulab123456789012']
 *   }
 *   transform {
 *     ulabel_uids = ['ulab123456789012']
 *   }
 * }
 *
 * <h3>Named record resolution</h3>
 *
 * Fields that accept UIDs (project_uids, ulabel_uids, space_uid, branch_uid,
 * and ulabel_uids inside run/transform/artifact rule blocks) also accept
 * name-based references using a prefix:
 *
 * <ul>
 *   <li>{@code ?name} – look up by name; if not found, log a warning and skip</li>
 *   <li>{@code !name} – look up by name; if not found, throw an error</li>
 *   <li>{@code +name} – look up by name; if not found, create the record</li>
 * </ul>
 *
 * Example:
 * <pre>
 *   project_uids = ['+my-project']     // create if missing
 *   ulabel_uids  = ['!my-label']       // fail if missing
 *   space_uid    = '?my-space'         // skip if missing
 *   branch_uid   = '!my-branch'        // fail if missing
 * </pre>
 */
@ScopeName('lamin')
@Description('''
    The `lamin` scope allows you to configure the `nf-lamin` plugin.
''')
@Slf4j
@CompileStatic
class LaminConfig implements ConfigScope {

    @ConfigOption
    @Description('''
        The instance for the Lamin API (format: 'owner/repo').
    ''')
    final String instance

    @ConfigOption
    @Description('''
        The access token for the Lamin API.
    ''')
    final String api_key

    @ConfigOption
    @Description('''
        List of project UIDs to link to all artifacts, runs, and transforms.
        Also accepts named references (see "Named record resolution" above).
    ''')
    final List<String> project_uids

    @ConfigOption
    @Description('''
        List of ulabel UIDs to link to all artifacts, runs, and transforms.
        Also accepts named references (see "Named record resolution" above).
    ''')
    final List<String> ulabel_uids

    @ConfigOption
    @Description('''
        The UID of the space to use for all transforms, runs, and artifacts.
        Also accepts named references (see "Named record resolution" above).
    ''')
    final String space_uid

    @ConfigOption
    @Description('''
        The UID of the branch to use for all transforms, runs, and artifacts.
        Also accepts named references (see "Named record resolution" above).
    ''')
    final String branch_uid

    @ConfigOption
    @Description('''
        (Advanced) The environment for the Lamin API (default: 'prod').
    ''')
    final String env

    @ConfigOption
    @Description('''
        (Advanced) API connection settings including Supabase URL/key and retry configuration.
    ''')
    final ApiConfig api

    @ConfigOption
    @Description('''
        (Advanced) Manually specify a transform UID if known. If provided, the plugin will use this existing transform instead of looking up or creating a new one.
    ''')
    final String transform_uid

    @ConfigOption
    @Description('''
        (Advanced) Manually specify a run UID if known. If provided, the plugin will use this existing run instead of creating a new one. The run must have status SCHEDULED (-3), otherwise a warning will be logged and a new run will be created.
    ''')
    final String run_uid

    @ConfigOption
    @Description('''
        (Advanced) Enable dry-run mode. When true, the plugin will not create any transforms, runs, or artifacts in LaminDB. Useful for testing configuration without modifying the database (default: false).
    ''')
    final Boolean dry_run

    @ConfigOption
    @Description('''
        Configuration for artifact tracking (both inputs and outputs). Use this for rules that apply to all artifacts regardless of direction.
    ''')
    final ArtifactConfig artifacts

    @ConfigOption
    @Description('''
        Configuration for input artifact tracking. Use this to control which input files are tracked and what metadata is attached.
    ''')
    final ArtifactConfig input_artifacts

    @ConfigOption
    @Description('''
        Configuration for output artifact tracking. Use this to control which output files are tracked and what metadata is attached.
    ''')
    final ArtifactConfig output_artifacts

    @ConfigOption
    @Description('''
        Configuration for run-specific metadata linking. Allows specifying project and ulabel UIDs to link to runs.
    ''')
    final RunConfig run

    @ConfigOption
    @Description('''
        Configuration for transform-specific metadata linking. Allows specifying project and ulabel UIDs to link to transforms.
    ''')
    final TransformConfig transform

    @ConfigOption
    @Description('''
        (Advanced) Feature flags for enabling or disabling optional plugin features.
    ''')
    final FeaturesConfig features

    /* required by extension point -- do not remove */
    LaminConfig() {}

    /**
     * Configuration for Lamin API integration
     * @param opts the configuration options map
     */
    LaminConfig(Map opts) {
        // Extract values from map or environment variables
        // Use containsKey to distinguish between "not provided" vs "explicitly null/empty"
        this.instance = opts.containsKey('instance') ? opts.instance : System.getenv('LAMIN_CURRENT_INSTANCE')
        this.api_key = opts.containsKey('api_key') ? opts.api_key : System.getenv('LAMIN_API_KEY')

        // Parse project_uids, ulabel_uids
        this.project_uids = parseUidList(opts.containsKey('project_uids') ? opts.project_uids : System.getenv('LAMIN_CURRENT_PROJECT'))
        this.ulabel_uids = parseUidList(opts.containsKey('ulabel_uids') ? opts.ulabel_uids : null)

        // Parse space and branch (UID only)
        this.space_uid = opts.space_uid
        this.branch_uid = opts.branch_uid

        this.env = opts.containsKey('env') ? (opts.env ?: 'prod') : (System.getenv('LAMIN_ENV') ?: 'prod')

        // Parse api configuration
        this.api = opts.containsKey('api') ? new ApiConfig(opts.api as Map) : new ApiConfig()

        this.transform_uid = opts.containsKey('transform_uid') ? opts.transform_uid : System.getenv('LAMIN_TRANSFORM_UID')
        this.run_uid = opts.containsKey('run_uid') ? opts.run_uid : System.getenv('LAMIN_RUN_UID')
        this.dry_run = opts.containsKey('dry_run') ? (opts.dry_run as Boolean) : ((System.getenv('LAMIN_DRY_RUN') as Boolean) ?: false)

        // Parse artifact configurations
        this.artifacts = opts.containsKey('artifacts') ? new ArtifactConfig(opts.artifacts as Map, 'both') : null
        this.input_artifacts = opts.containsKey('input_artifacts') ? new ArtifactConfig(opts.input_artifacts as Map, 'input') : null
        this.output_artifacts = opts.containsKey('output_artifacts') ? new ArtifactConfig(opts.output_artifacts as Map, 'output') : null

        // Parse run and transform configurations
        this.run = opts.containsKey('run') ? new RunConfig(opts.run as Map) : new RunConfig()
        this.transform = opts.containsKey('transform') ? new TransformConfig(opts.transform as Map) : new TransformConfig()

        // Parse feature flags
        this.features = opts.containsKey('features') ? new FeaturesConfig(opts.features as Map) : new FeaturesConfig()

        validateConfiguration()
    }

    /**
     * Validate the configuration parameters
     * @throws IllegalArgumentException if validation fails
     */
    private void validateConfiguration() {
        // Validation
        if (!this.instance?.trim()) {
            throw new IllegalArgumentException('Lamin instance is not set. Please set the "lamin.instance" in your nextflow.config file.')
        }
        if (!this.api_key?.trim()) {
            throw new IllegalArgumentException('Lamin API key is not set. Please set the "lamin.api_key" in your nextflow.config file.')
        }

        // check if instance is <owner>/<repo>
        if (!this.instance.matches(/^[\w.-]+\/[\w.-]+$/)) {
            throw new IllegalArgumentException("Provided Lamin instance ${this.instance} is not valid. Please provide a valid instance in the format <owner>/<repo>.")
        }

        // Validate artifact config mutual exclusivity
        boolean hasGlobalArtifacts = this.artifacts != null
        boolean hasDirectionSpecific = this.input_artifacts != null || this.output_artifacts != null
        if (hasGlobalArtifacts && hasDirectionSpecific) {
            throw new IllegalArgumentException(
                "Cannot use both 'artifacts' and 'input_artifacts'/'output_artifacts' configurations. " +
                "Use 'artifacts' for rules that apply to all artifacts, or use 'input_artifacts' and/or 'output_artifacts' for direction-specific rules."
            )
        }
    }

    /**
     * Get the instance for the Lamin API
     * @return the instance
     */
    String getInstance() {
        return this.instance
    }

    /**
     * Get the instance owner for the Lamin API
     * @return the instance owner
     */
    String getInstanceOwner() {
        return this.instance.split('/')[0]
    }

    /**
     * Get the instance name for the Lamin API
     * @return the instance name
     */
    String getInstanceName() {
        return this.instance.split('/')[1]
    }

    /**
     * Get the API key for LaminHub
     * @return the API key
     */
    String getApiKey() {
        return this.api_key
    }

    /**
     * Get the list of project UIDs
     * @return list of project UIDs
     */
    List<String> getProjectUids() {
        return this.project_uids ?: []
    }

    /**
     * Get the list of ulabel UIDs
     * @return list of ulabel UIDs
     */
    List<String> getUlabelUids() {
        return this.ulabel_uids ?: []
    }

    /**
     * Get the space UID
     * @return the space UID, or null if not set
     */
    String getSpaceUid() {
        return this.space_uid
    }

    /**
     * Get the branch UID
     * @return the branch UID, or null if not set
     */
    String getBranchUid() {
        return this.branch_uid
    }

    /**
     * Get the environment for the Lamin API
     * @return the environment
     */
    String getEnv() {
        return this.env
    }

    /**
     * Get the manually specified transform UID
     * @return the transform UID, or null if not specified
     */
    String getTransformUid() {
        return this.transform_uid
    }

    /**
     * Get the manually specified run UID
     * @return the run UID, or null if not specified
     */
    String getRunUid() {
        return this.run_uid
    }

    /**
     * Get the dry-run mode setting
     * @return true if dry-run mode is enabled
     */
    Boolean getDryRun() {
        return this.dry_run
    }

    /**
     * Get the artifact configuration (both inputs and outputs)
     * @return the artifact configuration, or null if not set
     */
    ArtifactConfig getArtifacts() {
        return this.artifacts
    }

    /**
     * Get the input artifact configuration
     * @return the input artifact configuration, or null if not set
     */
    ArtifactConfig getInputArtifacts() {
        return this.input_artifacts
    }

    /**
     * Get the output artifact configuration
     * @return the output artifact configuration, or null if not set
     */
    ArtifactConfig getOutputArtifacts() {
        return this.output_artifacts
    }

    /**
     * Get the run configuration
     * @return the run configuration
     */
    RunConfig getRunConfig() {
        return this.run ?: new RunConfig()
    }

    /**
     * Get the transform configuration
     * @return the transform configuration
     */
    TransformConfig getTransformConfig() {
        return this.transform ?: new TransformConfig()
    }

    /**
     * Get the API configuration
     * @return the API configuration
     */
    ApiConfig getApiConfig() {
        return this.api ?: new ApiConfig()
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
            // Support comma-separated values from env var
            return value.split(',').collect { it?.trim() }.findAll { it }
        }
        return []
    }

    /**
     * Parse configuration from a Nextflow session
     * @param session the Nextflow session
     * @return the parsed LaminConfig
     */
    static LaminConfig parseConfig(Session session) {
        Map configMap = session?.config?.lamin as Map ?: [:]

        if (!configMap.instance && !System.getenv('LAMIN_CURRENT_INSTANCE')) {
            throw new IllegalArgumentException('Lamin instance is not set. Please set the "lamin.instance" in your nextflow.config file.')
        }

        return new LaminConfig(configMap)
    }

    /**
     * Parse configuration from a Map
     * @param configMap the configuration map
     * @return the parsed LaminConfig
     */
    static LaminConfig parseConfig(Map configMap) {
        return new LaminConfig(configMap)
    }

    /**
     * Create a string representation with masked sensitive data
     * @return string representation
     */
    @Override
    String toString() {
        def maskedApiKey = api_key?.size() > 6 ? api_key[0..1] + '****' + api_key[-2..-1] : 'ap****ed'

        return "LaminConfig{" +
            "instance='${instance}', " +
            "api_key='${maskedApiKey}', " +
            "project_uids=${project_uids}, " +
            "ulabel_uids=${ulabel_uids}, " +
            "space_uid='${space_uid}', " +
            "branch_uid='${branch_uid}', " +
            "env='${env}', " +
            "api=${api}, " +
            "transform_uid='${transform_uid}', " +
            "run_uid='${run_uid}', " +
            "run=${run}, " +
            "transform=${transform}" +
            "}"
    }
}
