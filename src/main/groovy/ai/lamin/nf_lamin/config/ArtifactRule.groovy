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
import groovy.util.logging.Slf4j
import java.util.regex.Pattern
import nextflow.config.schema.ConfigOption
import nextflow.script.dsl.Description

/**
 * Configuration for an individual artifact tracking rule.
 *
 * Rules define pattern-specific configurations for artifact tracking,
 * including whether to track matching files and what metadata to attach.
 *
 * Example:
 * <pre>
 * rules {
 *   fastqs {
 *     enabled = true
 *     pattern = '.*\\.fastq\\.gz$'
 *     direction = 'input'
 *     kind = 'dataset'
 *     ulabel_uids = ['GfQLm1Zi', 'vdkZXcrp']
 *     order = 1
 *   }
 * }
 * </pre>
 */
@Slf4j
@CompileStatic
class ArtifactRule {

    /** Name of this rule (the block key in the config, set by ArtifactConfig). */
    final String name

    @ConfigOption
    @Description('''
        Whether this rule is enabled (default: true).
    ''')
    final Boolean enabled

    @ConfigOption
    @Description('''
        Regular expression pattern to match file paths.
        Either pattern or paths must be specified.
    ''')
    final String pattern

    /**
     * Compiled regex pattern (for performance)
     */
    final Pattern compiledPattern

    @ConfigOption
    @Description('''
        Rule type: 'include' to track, 'exclude' to skip (default: 'include').
    ''')
    final String type

    @ConfigOption
    @Description('''
        Direction: 'input', 'output', or 'both' (default: 'both').
    ''')
    final String direction

    @ConfigOption
    @Description('''
        Kind of artifact (e.g., 'dataset', 'model', 'report').
    ''')
    final String kind

    @ConfigOption
    @Description('''
        List of ULabel UIDs to attach to matching artifacts.
        Also accepts named references (see "Named record resolution" in plugin docs).
    ''')
    final List<String> ulabelUids

    @ConfigOption
    @Description('''
        Rule evaluation order (lower numbers = higher priority, default: 100).
    ''')
    final Integer order

    @ConfigOption(types=[String, Closure, Map])
    @Description('''
        Key template or closure for deriving artifact keys from file paths.
        Supports String templates with variables ({basename}, {filename}, {ext},
        {parent}, {parent.parent}, etc.), a Closure that receives a Path and
        returns a String, or a Map shorthand like `[relativize: params.outdir]`.
        If null, inherits the global key from ArtifactConfig.
    ''')
    final Object key

    @ConfigOption(types=[String, List, Closure])
    @Description('''
        One or more file paths to explicitly track as artifacts.
        Paths are resolved using Nextflow's FileHelper.asPath. Input artifact
        paths are resolved at the beginning of the workflow, and output artifact
        paths are resolved at the end. Can be a single string, a list of strings,
        or a Closure returning a string or list. Use a closure when the value
        depends on workflow params: `include_paths = { params.input }`.
        If the resolved value is null (e.g. optional param not set), the rule is skipped.
    ''')
    final List<String> include_paths

    /** Closure that produces paths lazily at runtime (set when include_paths = { ... } is used). */
    final Closure pathsClosure

    /**
     * Create a new ArtifactRule from configuration map
     * @param opts Configuration options
     */
    ArtifactRule(Map opts) {
        this.name = opts.name as String
        this.enabled = opts.containsKey('enabled') ? (opts.enabled as Boolean) : true
        this.pattern = opts.pattern as String
        this.type = opts.containsKey('type') ? (opts.type as String) : 'include'
        this.direction = opts.containsKey('direction') ? (opts.direction as String) : 'both'
        this.kind = opts.kind as String
        this.key = opts.key  // keep as-is: String template or Closure
        this.order = opts.containsKey('order') ? (opts.order as Integer) : 100

        // Parse list fields (can be String, List, or Closure)
        this.ulabelUids = ConfigUtils.parseStringOrList(opts.ulabel_uids)
        if (opts.include_paths instanceof Closure) {
            this.pathsClosure = opts.include_paths as Closure
            this.include_paths = []
        } else {
            this.pathsClosure = null
            this.include_paths = ConfigUtils.parseStringOrList(opts.include_paths)
        }

        // Validate configuration
        validate()

        // Compile pattern (may be null when paths is used instead)
        this.compiledPattern = ConfigUtils.compilePattern(this.pattern, 'pattern')
    }

    /**
     * Validate the rule configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    private void validate() {
        boolean hasPaths = hasPaths()
        boolean hasPattern = this.pattern?.trim()

        if (!hasPaths && !hasPattern) {
            throw new IllegalArgumentException("ArtifactRule '${this.name}': either 'pattern' or 'include_paths' must be specified")
        }

        if (this.type && !['include', 'exclude'].contains(this.type)) {
            throw new IllegalArgumentException("Rule type must be 'include' or 'exclude', got '${this.type}'")
        }

        if (this.direction && !['input', 'output', 'both'].contains(this.direction)) {
            throw new IllegalArgumentException("Rule direction must be 'input', 'output', or 'both', got '${this.direction}'")
        }
    }

    /**
     * Check if this rule matches the given file path
     * @param path File path to check
     * @return true if the path matches the pattern
     */
    boolean matches(String path) {
        if (!enabled || compiledPattern == null) {
            return false
        }
        return compiledPattern.matcher(path).matches()
    }

    /**
     * Check if this rule applies to the given direction
     * @param artifactDirection 'input' or 'output'
     * @return true if the rule applies to this direction
     */
    boolean appliesToDirection(String artifactDirection) {
        if (!enabled) {
            return false
        }
        return this.direction == 'both' || this.direction == artifactDirection
    }

    /**
     * Check if this rule has explicit paths to track (including closure-based paths).
     * @return true if the rule has paths
     */
    boolean hasPaths() {
        return pathsClosure != null || (include_paths != null && !include_paths.isEmpty())
    }

    /**
     * Resolve and return the paths for this rule, evaluating the closure if needed.
     * @param workflowParams Nextflow workflow params (session.params), used as the 'params'
     *        variable inside a closure when the user writes `paths = { params.input }`.
     * @return List of resolved path strings
     */
    List<String> resolvePaths(Map workflowParams) {
        if (pathsClosure != null) {
            return ConfigUtils.parseStringOrList(ConfigUtils.evalClosureWithParams(pathsClosure, workflowParams))
        }
        return include_paths
    }

    @Override
    String toString() {
        return "ArtifactRule{" +
            "name='${name}', " +
            "enabled=${enabled}, " +
            "pattern='${pattern}', " +
            "type='${type}', " +
            "direction='${direction}', " +
            "kind='${kind}', " +
            "ulabelUids=${ulabelUids}, " +
            "include_paths=${pathsClosure != null ? '<closure>' : include_paths}, " +
            "key='${key instanceof Closure ? '<closure>' : key}', " +
            "order=${order}" +
            "}"
    }
}
