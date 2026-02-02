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
 *   project_uids = ['global-project']
 *
 *   rules {
 *     fastqs {
 *       enabled = true
 *       pattern = '.*\\.fastq\\.gz$'
 *       ulabel_uids = ['fastq-label']
 *       project_uids = ['fastq-project']
 *       kind = 'dataset'
 *     }
 *     bams {
 *       enabled = false
 *       pattern = '.*\\.bam$'
 *     }
 *   }
 * }
 * </pre>
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
@Slf4j
@CompileStatic
class ArtifactConfig {

    /**
     * Whether artifact tracking is enabled for this config (default: true)
     */
    final Boolean enabled

    /**
     * Global include pattern (regex). Files must match this to be tracked.
     */
    final String includePattern

    /**
     * Compiled include pattern (for performance)
     */
    final Pattern compiledIncludePattern

    /**
     * Global exclude pattern (regex). Files matching this will not be tracked.
     */
    final String excludePattern

    /**
     * Compiled exclude pattern (for performance)
     */
    final Pattern compiledExcludePattern

    /**
     * Global list of ULabel UIDs to attach to all artifacts
     */
    final List<String> ulabelUids

    /**
     * Global list of Project UIDs to attach to all artifacts
     */
    final List<String> projectUids

    /**
     * Global artifact kind (e.g., 'dataset', 'model')
     */
    final String kind

    /**
     * Path-specific rules (map of rule name to ArtifactRule)
     */
    final Map<String, ArtifactRule> rules

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
     * @param opts Configuration options
     * @param direction Direction ('input', 'output', or 'both')
     */
    ArtifactConfig(Map opts, String direction = 'both') {
        this.direction = direction
        this.enabled = opts.containsKey('enabled') ? (opts.enabled as Boolean) : true
        this.includePattern = opts.include_pattern as String
        this.excludePattern = opts.exclude_pattern as String
        this.kind = opts.kind as String

        // Parse list fields (can be String or List)
        this.ulabelUids = ConfigUtils.parseStringOrList(opts.ulabel_uids)
        this.projectUids = ConfigUtils.parseStringOrList(opts.project_uids)

        // Compile patterns
        this.compiledIncludePattern = ConfigUtils.compilePattern(this.includePattern, 'include_pattern')
        this.compiledExcludePattern = ConfigUtils.compilePattern(this.excludePattern, 'exclude_pattern')

        // Parse rules
        this.rules = parseRules(opts.rules as Map, direction)
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
     * 2. Initialize state with global metadata (ulabels, projects, kind) and shouldTrack=true
     * 3. Apply global exclude_pattern → if matches, set shouldTrack=false
     * 4. Apply global include_pattern → if set and doesn't match, set shouldTrack=false
     * 5. Iterate through rules in priority order, each can modify shouldTrack and metadata
     *
     * For ulabel_uids and project_uids: values are accumulated (merged) from all sources.
     * For kind: later rules override earlier values.
     * For shouldTrack: each matching rule can override the decision - include rules set it to true,
     *                  exclude rules set it to false. The last matching rule with a type wins.
     *
     * @param path File path to evaluate
     * @param artifactDirection 'input' or 'output'
     * @return ArtifactEvaluation with shouldTrack flag and metadata map
     */
    ArtifactEvaluation evaluate(String path, String artifactDirection) {
        // Early exit if disabled or direction doesn't match
        if (!enabled) {
            log.debug "Path '${path}' not tracked: artifact tracking is disabled"
            return new ArtifactEvaluation(false, [:])
        }
        if (this.direction != 'both' && this.direction != artifactDirection) {
            log.debug "Path '${path}' not tracked: direction '${artifactDirection}' doesn't match config direction '${this.direction}'"
            return new ArtifactEvaluation(false, [:])
        }

        // Initialize state with global metadata
        List<String> ulabels = new ArrayList<>(this.ulabelUids)
        List<String> projects = new ArrayList<>(this.projectUids)
        String artifactKind = this.kind
        boolean shouldTrack = true

        // Apply global exclude pattern
        if (compiledExcludePattern && compiledExcludePattern.matcher(path).matches()) {
            log.debug "Path '${path}' excluded by global exclude_pattern"
            shouldTrack = false
        }

        // Apply global include pattern (only if still tracking)
        if (shouldTrack && compiledIncludePattern && !compiledIncludePattern.matcher(path).matches()) {
            log.debug "Path '${path}' does not match global include_pattern"
            shouldTrack = false
        }

        // Apply all matching rules in order
        for (ArtifactRule rule : sortedRules) {
            if (rule.matches(path) && rule.appliesToDirection(artifactDirection)) {
                log.debug "Path '${path}' matched rule: ${rule}"

                if (rule.type) {
                    if (rule.type == "exclude") {
                        log.debug "Path '${path}' excluded by rule"
                        shouldTrack = false
                    } else if (rule.type == "include") {
                        log.debug "Path '${path}' included by rule"
                        shouldTrack = true
                    }
                }

                // Accumulate metadata from matching rules
                if (rule.ulabelUids) {
                    ulabels.addAll(rule.ulabelUids)
                }
                if (rule.projectUids) {
                    projects.addAll(rule.projectUids)
                }
                if (rule.kind) {
                    artifactKind = rule.kind
                }
            }
        }

        // Build metadata map (always return accumulated metadata)
        Map<String, Object> metadata = [:]
        metadata.ulabel_uids = ulabels.unique()
        metadata.project_uids = projects.unique()
        if (artifactKind) {
            metadata.kind = artifactKind
        }

        return new ArtifactEvaluation(shouldTrack, metadata)
    }

    @Override
    String toString() {
        return "ArtifactConfig{" +
            "enabled=${enabled}, " +
            "direction='${direction}', " +
            "includePattern='${includePattern}', " +
            "excludePattern='${excludePattern}', " +
            "ulabelUids=${ulabelUids}, " +
            "projectUids=${projectUids}, " +
            "kind='${kind}', " +
            "rules=${rules.size()} rules" +
            "}"
    }
}
