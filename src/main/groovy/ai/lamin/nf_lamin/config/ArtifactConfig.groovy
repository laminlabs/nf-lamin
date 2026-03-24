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
import java.nio.file.Path
import java.util.regex.Pattern
import nextflow.config.schema.ConfigOption
import nextflow.script.dsl.Description

/**
 * Configuration for artifact tracking (input, output, or both).
 *
 * Supports global settings and path-specific rules for fine-grained control
 * over which files are tracked and what metadata is attached.
 *
 * Example:
 * <pre>
 * artifacts {
 *   enabled = true
 *   include_pattern = '.*\\.(fastq|bam)$'
 *   exclude_pattern = '.*temp.*'
 *   ulabel_uids = ['global-label']
 *
 *   rules {
 *     fastqs {
 *       enabled = true
 *       pattern = '.*\\.fastq\\.gz$'
 *       ulabel_uids = ['fastq-label']
 *       kind = 'dataset'
 *     }
 *     bams {
 *       enabled = false
 *       pattern = '.*\\.bam$'
 *     }
 *   }
 * }
 * </pre>
 */
@Slf4j
@CompileStatic
class ArtifactConfig {

    @ConfigOption
    @Description('''
        Whether artifact tracking is enabled for this config (default: true).
    ''')
    final Boolean enabled

    @ConfigOption
    @Description('''
        Whether to track local (file://) artifacts (default: true).
    ''')
    final Boolean includeLocal

    @ConfigOption
    @Description('''
        Whether to exclude artifacts in the Nextflow workdir (default: true). Intermediate files between processes live here.
    ''')
    final Boolean excludeWorkDir

    @ConfigOption
    @Description('''
        Whether to exclude artifacts in the Nextflow assets directory (default: true). Pipeline source files live here.
    ''')
    final Boolean excludeAssetsDir

    @ConfigOption
    @Description('''
        Global include pattern (regex). Files must match this to be tracked.
    ''')
    final String includePattern

    /**
     * Compiled include pattern (for performance)
     */
    final Pattern compiledIncludePattern

    @ConfigOption
    @Description('''
        Global exclude pattern (regex). Files matching this will not be tracked.
    ''')
    final String excludePattern

    /**
     * Compiled exclude pattern (for performance)
     */
    final Pattern compiledExcludePattern

    @ConfigOption
    @Description('''
        List of ULabel UIDs to attach to all artifacts matched by this config.
    ''')
    final List<String> ulabelUids

    @ConfigOption
    @Description('''
        Global artifact kind (e.g., 'dataset', 'model', 'report').
    ''')
    final String kind

    @ConfigOption(types=[String, Closure, Map])
    @Description('''
        Key template or closure for deriving artifact keys from file paths. Supports String templates with variables ({basename}, {filename}, {ext}, {parent}, {parent.parent}, etc.), a Closure that receives a Path and returns a String, or a Map shorthand like `[relativize: params.outdir]`.
    ''')
    final Object key

    @ConfigOption
    @Description('''
        Path-specific rules for fine-grained control over artifact tracking. Each rule can match files by pattern and override tracking decisions and metadata.
    ''')
    final Map<String, ArtifactRule> rules

    @ConfigOption
    @Description('''
        List of file paths or glob patterns to explicitly track as artifacts.
        Paths are resolved using Nextflow's FileHelper.asPath. Input artifact
        paths are resolved at the beginning of the workflow, and output artifact
        paths are resolved at the end. Can be a single string or a list of strings.
    ''')
    final List<String> include_paths

    /**
     * Sorted list of rules (by order) for evaluation
     */
    final List<ArtifactRule> sortedRules

    /**
     * Direction of this config: 'input', 'output', or 'both'
     * This is set when creating configs from lamin.input_artifacts or lamin.output_artifacts
     */
    final String direction

    /**
     * Create a new ArtifactConfig from configuration map
     * @param opts Configuration options (null treated as empty map)
     * @param direction Direction ('input', 'output', or 'both')
     */
    ArtifactConfig(Map opts, String direction = 'both') {
        // Treat null as empty map to prevent NPE
        Map safeOpts = opts ?: [:]
        this.direction = direction
        this.enabled = safeOpts.containsKey('enabled') ? (safeOpts.enabled as Boolean) : true
        this.includeLocal = safeOpts.containsKey('include_local') ? (safeOpts.include_local as Boolean) : true
        this.excludeWorkDir = safeOpts.containsKey('exclude_work_dir') ? (safeOpts.exclude_work_dir as Boolean) : true
        this.excludeAssetsDir = safeOpts.containsKey('exclude_assets_dir') ? (safeOpts.exclude_assets_dir as Boolean) : true
        this.includePattern = safeOpts.include_pattern as String
        this.excludePattern = safeOpts.exclude_pattern as String
        this.kind = safeOpts.kind as String
        this.key = safeOpts.key  // keep as-is: String template or Closure

        // Parse list fields (can be String or List)
        this.ulabelUids = ConfigUtils.parseStringOrList(safeOpts.ulabel_uids)
        this.include_paths = ConfigUtils.parseStringOrList(safeOpts.include_paths)

        // Compile patterns
        this.compiledIncludePattern = ConfigUtils.compilePattern(this.includePattern, 'include_pattern')
        this.compiledExcludePattern = ConfigUtils.compilePattern(this.excludePattern, 'exclude_pattern')

        // Parse rules
        this.rules = parseRules(safeOpts.rules as Map, direction)
        this.sortedRules = this.rules.values().toList().sort { it.order }

        log.debug "Created ArtifactConfig with ${this.rules.size()} rules for direction '${direction}'"
    }

    /**
     * Parse rules from configuration map
     * @param rulesMap Map of rule name to rule configuration
     * @param direction Direction to apply to rules
     * @return Map of rule name to ArtifactRule
     */
    private static Map<String, ArtifactRule> parseRules(Map rulesMap, String direction) {
        if (!rulesMap) {
            return [:]
        }

        Map<String, ArtifactRule> parsed = [:]
        rulesMap.each { key, value ->
            String name = key as String
            def ruleOpts = value
            if (ruleOpts instanceof Map) {
                Map ruleMap = ruleOpts as Map
                // Set direction from parent config if not specified in rule
                if (!ruleMap.containsKey('direction')) {
                    ruleMap.direction = direction
                }
                ruleMap.name = name
                ArtifactRule rule = new ArtifactRule(ruleMap)
                parsed.put(name, rule)
            } else {
                log.warn "Rule '${name}' is not a map, skipping"
            }
        }
        return parsed
    }

    /**
     * Check if artifact tracking is enabled
     * @return true if enabled
     */
    boolean isEnabled() {
        return this.enabled
    }

    /**
     * Evaluate a file path against all rules and return the tracking decision with metadata.
     *
     * Evaluation flow:
     * 1. If disabled or direction doesn't match → return (false, empty metadata)
     * 2. Initialize state with global metadata (ulabels, kind) and shouldTrack=true
     * 3. Apply global exclude_pattern → if matches, set shouldTrack=false
     * 4. Apply global include_pattern → if set and doesn't match, set shouldTrack=false
     * 5. Iterate through rules in priority order, each can modify shouldTrack and metadata
     *
     * For ulabel_uids: values are accumulated (merged) from all sources.
     * For kind: later rules override earlier values.
     * For shouldTrack: each matching rule can override the decision - include rules set it to true,
     *                  exclude rules set it to false. The last matching rule with a type wins.
     *
     * @param path The Path object for the artifact file
     * @param artifactDirection 'input' or 'output'
     * @return ArtifactEvaluation with shouldTrack flag and metadata map
     */
    ArtifactEvaluation evaluate(Path path, String artifactDirection) {
        String pathStr = path.toUri().toString()

        // Early exit if disabled or direction doesn't match
        if (!enabled) {
            log.debug "Path '${pathStr}' not tracked: artifact tracking is disabled"
            return ArtifactEvaluation.notTracked()
        }
        if (this.direction != 'both' && this.direction != artifactDirection) {
            log.debug "Path '${pathStr}' not tracked: direction '${artifactDirection}' doesn't match config direction '${this.direction}'"
            return ArtifactEvaluation.notTracked()
        }

        // Initialize state with global metadata
        List<String> ulabels = new ArrayList<>(this.ulabelUids)
        String artifactKind = this.kind
        Object effectiveKeyConfig = this.key
        boolean shouldTrack = true

        // Apply global exclude pattern
        if (compiledExcludePattern && compiledExcludePattern.matcher(pathStr).matches()) {
            log.debug "Path '${pathStr}' excluded by global exclude_pattern"
            shouldTrack = false
        }

        // Apply global include pattern (only if still tracking)
        if (shouldTrack && compiledIncludePattern && !compiledIncludePattern.matcher(pathStr).matches()) {
            log.debug "Path '${pathStr}' does not match global include_pattern"
            shouldTrack = false
        }

        // Apply all matching rules in order
        for (ArtifactRule rule : sortedRules) {
            if (rule.matches(pathStr) && rule.appliesToDirection(artifactDirection)) {
                log.debug "Path '${pathStr}' matched rule: ${rule}"

                if (rule.type) {
                    if (rule.type == "exclude") {
                        log.debug "Path '${pathStr}' excluded by rule"
                        shouldTrack = false
                    } else if (rule.type == "include") {
                        log.debug "Path '${pathStr}' included by rule"
                        shouldTrack = true
                    }
                }

                // Accumulate metadata from matching rules
                if (rule.ulabelUids) {
                    ulabels.addAll(rule.ulabelUids)
                }
                if (rule.kind) {
                    artifactKind = rule.kind
                }
                if (rule.key != null) {
                    effectiveKeyConfig = rule.key
                }
            }
        }

        if (!shouldTrack) {
            return ArtifactEvaluation.notTracked()
        }
        String resolvedKey = null
        if (effectiveKeyConfig != null) {
            resolvedKey = KeyResolver.resolveKey(effectiveKeyConfig, path)
        }

        return new ArtifactEvaluation(
            shouldTrack,
            ulabels.unique(),
            artifactKind,
            resolvedKey
        )
    }

    /**
     * Collect all explicit paths to track from this config and its rules for a given direction.
     *
     * Returns a list of maps, each containing:
     *   - path (String): the path string to resolve
     *   - evaluation (ArtifactEvaluation): pre-built evaluation with metadata from the config/rule
     *
     * @param artifactDirection 'input' or 'output'
     * @return List of maps with path and evaluation info
     */
    List<Map<String, Object>> collectPaths(String artifactDirection, Map workflowParams) {
        if (!enabled) {
            return []
        }
        if (this.direction != 'both' && this.direction != artifactDirection) {
            return []
        }

        List<Map<String, Object>> result = []

        // Collect paths from config-level include_paths
        if (this.include_paths) {
            for (String pathStr : this.include_paths) {
                result.add([
                    path: pathStr,
                    evaluation: new ArtifactEvaluation(
                        true,
                        new ArrayList<>(this.ulabelUids),
                        this.kind,
                        null  // key resolved later from path
                    )
                ] as Map<String, Object>)
            }
        }

        // Collect paths from rules
        for (ArtifactRule rule : sortedRules) {
            if (!rule.enabled || !rule.hasPaths() || !rule.appliesToDirection(artifactDirection)) {
                continue
            }
            // Merge ulabels from config + rule
            List<String> mergedUlabels = new ArrayList<>(this.ulabelUids)
            if (rule.ulabelUids) {
                mergedUlabels.addAll(rule.ulabelUids)
            }
            mergedUlabels = mergedUlabels.unique() as List<String>

            String effectiveKind = rule.kind ?: this.kind

            for (String pathStr : rule.resolvePaths(workflowParams)) {
                result.add([
                    path: pathStr,
                    evaluation: new ArtifactEvaluation(
                        true,
                        mergedUlabels,
                        effectiveKind,
                        null  // key resolved later from path
                    )
                ] as Map<String, Object>)
            }
        }

        return result
    }

    @Override
    String toString() {
        return "ArtifactConfig{" +
            "enabled=${enabled}, " +
            "includeLocal=${includeLocal}, " +
            "excludeWorkDir=${excludeWorkDir}, " +
            "excludeAssetsDir=${excludeAssetsDir}, " +
            "direction='${direction}', " +
            "includePattern='${includePattern}', " +
            "excludePattern='${excludePattern}', " +
            "ulabelUids=${ulabelUids}, " +
            "kind='${kind}', " +
            "key='${key instanceof Closure ? '<closure>' : key}', " +
            "include_paths=${include_paths}, " +
            "rules=${rules.size()} rules" +
            "}"
    }
}
