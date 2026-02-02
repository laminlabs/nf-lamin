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
import nextflow.config.schema.ConfigOption
import nextflow.config.schema.ConfigScope
import nextflow.config.schema.ScopeName
import nextflow.script.dsl.Description

import ai.lamin.nf_lamin.config.ArtifactConfig
import ai.lamin.nf_lamin.config.ApiConfig
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
 *   env = 'prod'
 *   dry_run = false
 *   run {
 *     project_uids = ['proj123456789012']
 *     ulabel_uids = ['ulab123456789012']
 *   }
 *   transform {
 *     project_uids = ['proj123456789012']
 *     ulabel_uids = ['ulab123456789012']
 *   }
 * }
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
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
    final String apiKey

    @ConfigOption
    @Description('''
        @deprecated Use project_uids instead. This field will be removed in a future version.
    ''')
    final String project

    @ConfigOption
    @Description('''
        List of project UIDs to link to all artifacts, runs, and transforms.
    ''')
    final List<String> projectUids

    @ConfigOption
    @Description('''
        List of ulabel UIDs to link to all artifacts, runs, and transforms.
    ''')
    final List<String> ulabelUids

    @ConfigOption
    @Description('''
        (Advanced) The environment for the Lamin API (default: 'prod').
    ''')
    final String env

    @ConfigOption
    @Description('''
        @deprecated Use api.supabase_api_url instead. This field will be removed in a future version.
    ''')
    final String supabaseApiUrl

    @ConfigOption
    @Description('''
        @deprecated Use api.supabase_anon_key instead. This field will be removed in a future version.
    ''')
    final String supabaseAnonKey

    @ConfigOption
    @Description('''
        @deprecated Use api.max_retries instead. This field will be removed in a future version.
    ''')
    final Integer maxRetries

    @ConfigOption
    @Description('''
        @deprecated Use api.retry_delay instead. This field will be removed in a future version.
    ''')
    final Integer retryDelay

    @ConfigOption
    @Description('''
        (Advanced) API connection settings including Supabase URL/key and retry configuration.
    ''')
    final ApiConfig api

    @ConfigOption
    @Description('''
        (Advanced) Manually specify a transform UID if known. If provided, the plugin will use this existing transform instead of looking up or creating a new one.
    ''')
    final String transformUid

    @ConfigOption
    @Description('''
        (Advanced) Manually specify a run UID if known. If provided, the plugin will use this existing run instead of creating a new one. The run must have status SCHEDULED (-3), otherwise a warning will be logged and a new run will be created.
    ''')
    final String runUid

    @ConfigOption
    @Description('''
        (Advanced) Enable dry-run mode. When true, the plugin will not create any transforms, runs, or artifacts in LaminDB. Useful for testing configuration without modifying the database (default: false).
    ''')
    final Boolean dryRun

    @ConfigOption
    @Description('''
        Configuration for artifact tracking (both inputs and outputs). Use this for rules that apply to all artifacts regardless of direction.
    ''')
    final ArtifactConfig artifacts

    @ConfigOption
    @Description('''
        Configuration for input artifact tracking. Use this to control which input files are tracked and what metadata is attached.
    ''')
    final ArtifactConfig inputArtifacts

    @ConfigOption
    @Description('''
        Configuration for output artifact tracking. Use this to control which output files are tracked and what metadata is attached.
    ''')
    final ArtifactConfig outputArtifacts

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
        this.apiKey = opts.containsKey('api_key') ? opts.api_key : System.getenv('LAMIN_API_KEY')

        // Handle deprecated 'project' field
        this.project = opts.containsKey('project') ? opts.project : null
        if (this.project) {
            log.warn "The 'project' configuration option is deprecated and will be removed in a future version. Use 'project_uids' instead."
        }

        // Parse project_uids and ulabel_uids
        this.projectUids = parseUidList(opts.containsKey('project_uids') ? opts.project_uids : System.getenv('LAMIN_CURRENT_PROJECT'))
        this.ulabelUids = parseUidList(opts.containsKey('ulabel_uids') ? opts.ulabel_uids : null)

        this.env = opts.containsKey('env') ? (opts.env ?: 'prod') : (System.getenv('LAMIN_ENV') ?: 'prod')

        // Handle deprecated API config fields with backward compatibility
        boolean hasOldApiConfig = opts.containsKey('supabase_api_url') || opts.containsKey('supabase_anon_key') ||
                                   opts.containsKey('max_retries') || opts.containsKey('retry_delay')
        if (hasOldApiConfig) {
            log.warn "The API configuration options (supabase_api_url, supabase_anon_key, max_retries, retry_delay) are deprecated. " +
                     "Please use the 'api' section instead: lamin { api { supabase_api_url = '...', ... } }"
            this.supabaseApiUrl = opts.containsKey('supabase_api_url') ? opts.supabase_api_url : System.getenv('SUPABASE_API_URL')
            this.supabaseAnonKey = opts.containsKey('supabase_anon_key') ? opts.supabase_anon_key : System.getenv('SUPABASE_ANON_KEY')
            this.maxRetries = opts.containsKey('max_retries') ? (opts.max_retries as Integer) : ((System.getenv('LAMIN_MAX_RETRIES') as Integer) ?: 3)
            this.retryDelay = opts.containsKey('retry_delay') ? (opts.retry_delay as Integer) : ((System.getenv('LAMIN_RETRY_DELAY') as Integer) ?: 100)
        } else {
            this.supabaseApiUrl = null
            this.supabaseAnonKey = null
            this.maxRetries = null
            this.retryDelay = null
        }

        // Parse api configuration (preferred approach)
        this.api = opts.containsKey('api') ? new ApiConfig(opts.api as Map) : new ApiConfig()

        this.transformUid = opts.containsKey('transform_uid') ? opts.transform_uid : System.getenv('LAMIN_TRANSFORM_UID')
        this.runUid = opts.containsKey('run_uid') ? opts.run_uid : System.getenv('LAMIN_RUN_UID')
        this.dryRun = opts.containsKey('dry_run') ? (opts.dry_run as Boolean) : ((System.getenv('LAMIN_DRY_RUN') as Boolean) ?: false)

        // Parse artifact configurations
        this.artifacts = opts.containsKey('artifacts') ? new ArtifactConfig(opts.artifacts as Map, 'both') : null
        this.inputArtifacts = opts.containsKey('input_artifacts') ? new ArtifactConfig(opts.input_artifacts as Map, 'input') : null
        this.outputArtifacts = opts.containsKey('output_artifacts') ? new ArtifactConfig(opts.output_artifacts as Map, 'output') : null

        // Parse run and transform configurations
        this.run = opts.containsKey('run') ? new RunConfig(opts.run as Map) : new RunConfig()
        this.transform = opts.containsKey('transform') ? new TransformConfig(opts.transform as Map) : new TransformConfig()

        validateConfiguration()
    }

    /**
     * Configuration for Lamin API integration (direct parameters)
     * @param instance the instance
     * @param apiKey the API key
     * @param project the project (optional)
     * @param env the environment (optional, defaults to 'prod')
     */
    LaminConfig(String instance, String apiKey, String project = null, String env = 'prod') {
        this([
            instance: instance,
            api_key: apiKey,
            project: project,
            env: env
        ])
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
        if (!this.apiKey?.trim()) {
            throw new IllegalArgumentException('Lamin API key is not set. Please set the "lamin.api_key" in your nextflow.config file.')
        }

        // check if instance is <owner>/<repo>
        if (!this.instance.matches(/^[\w.-]+\/[\w.-]+$/)) {
            throw new IllegalArgumentException("Provided Lamin instance ${this.instance} is not valid. Please provide a valid instance in the format <owner>/<repo>.")
        }

        // Validate artifact config mutual exclusivity
        boolean hasGlobalArtifacts = this.artifacts != null
        boolean hasDirectionSpecific = this.inputArtifacts != null || this.outputArtifacts != null
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
     * Get the API key for Lamin Hub
     * @return the API key
     */
    String getApiKey() {
        return this.apiKey
    }

    /**
     * Get the project for the Lamin API
     * @return the project
     * @deprecated Use getProjectUids() instead
     */
    @Deprecated
    String getProject() {
        return this.project
    }

    /**
     * Get the list of project UIDs
     * @return list of project UIDs
     */
    List<String> getProjectUids() {
        return this.projectUids ?: []
    }

    /**
     * Get the list of ulabel UIDs
     * @return list of ulabel UIDs
     */
    List<String> getUlabelUids() {
        return this.ulabelUids ?: []
    }

    /**
     * Get the environment for the Lamin API
     * @return the environment
     */
    String getEnv() {
        return this.env
    }

    /**
     * Get the Supabase API URL for the Lamin API
     * @return the Supabase API URL
     * @deprecated Use getApiConfig().getSupabaseApiUrl() instead
     */
    @Deprecated
    String getSupabaseApiUrl() {
        // Prefer deprecated field (for backward compatibility), then new api config
        return (this.supabaseApiUrl ?: api?.getSupabaseApiUrl())
    }

    /**
     * Get the Supabase Anon Key for the Lamin API
     * @return the Supabase Anon Key
     * @deprecated Use getApiConfig().getSupabaseAnonKey() instead
     */
    @Deprecated
    String getSupabaseAnonKey() {
        // Prefer deprecated field (for backward compatibility), then new api config
        return (this.supabaseAnonKey ?: api?.getSupabaseAnonKey())
    }

    /**
     * Get the maximum number of retries for API requests
     * @return the maximum number of retries
     * @deprecated Use getApiConfig().getMaxRetries() instead
     */
    @Deprecated
    Integer getMaxRetries() {
        // Prefer deprecated field (for backward compatibility), then new api config
        return (this.maxRetries ?: api?.getMaxRetries() ?: 3)
    }

    /**
     * Get the delay between retries for API requests
    /**
     * Get the delay between retries for API requests
     * @return the delay between retries in milliseconds
     * @deprecated Use getApiConfig().getRetryDelay() instead
     */
    @Deprecated
    Integer getRetryDelay() {
        // Prefer deprecated field (for backward compatibility), then new api config
        return (this.retryDelay ?: api?.getRetryDelay() ?: 100)
    }

    /**
     * Get the manually specified transform UID
     * @return the transform UID, or null if not specified
     */
    String getTransformUid() {
        return this.transformUid
    }

    /**
     * Get the manually specified run UID
     * @return the run UID, or null if not specified
     */
    String getRunUid() {
        return this.runUid
    }

    /**
     * Get the dry-run mode setting
     * @return true if dry-run mode is enabled
     */
    Boolean getDryRun() {
        return this.dryRun
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
        return this.inputArtifacts
    }

    /**
     * Get the output artifact configuration
     * @return the output artifact configuration, or null if not set
     */
    ArtifactConfig getOutputArtifacts() {
        return this.outputArtifacts
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
        def maskedApiKey = apiKey?.size() > 6 ? apiKey[0..1] + '****' + apiKey[-2..-1] : 'ap****ed'

        return "LaminConfig{" +
            "instance='${instance}', " +
            "apiKey='${maskedApiKey}', " +
            "projectUids=${projectUids}, " +
            "ulabelUids=${ulabelUids}, " +
            "env='${env}', " +
            "api=${api}, " +
            "transformUid='${transformUid}', " +
            "runUid='${runUid}', " +
            "run=${run}, " +
            "transform=${transform}" +
            "}"
    }
}
