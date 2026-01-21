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
 *     project_uids = ['abc123']
 *     order = 1
 *   }
 * }
 * </pre>
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
@Slf4j
@CompileStatic
class ArtifactRule {

    /**
     * Whether this rule is enabled (default: true)
     */
    final Boolean enabled

    /**
     * Regular expression pattern to match file paths
     */
    final String pattern

    /**
     * Compiled regex pattern (for performance)
     */
    final Pattern compiledPattern

    /**
     * Rule type: 'include' to track, 'exclude' to skip (default: 'include')
     */
    final String type

    /**
     * Direction: 'input', 'output', or 'both' (default: 'both')
     */
    final String direction

    /**
     * Kind of artifact (e.g., 'dataset', 'model', 'report')
     */
    final String kind

    /**
     * List of ULabel UIDs to attach to matching artifacts
     */
    final List<String> ulabelUids

    /**
     * List of Project UIDs to attach to matching artifacts
     */
    final List<String> projectUids

    /**
     * Rule evaluation order (lower numbers = higher priority, default: 100)
     */
    final Integer order

    /**
     * Create a new ArtifactRule from configuration map
     * @param opts Configuration options
     */
    ArtifactRule(Map opts) {
        this.enabled = opts.containsKey('enabled') ? (opts.enabled as Boolean) : true
        this.pattern = opts.pattern as String
        this.type = opts.containsKey('type') ? (opts.type as String) : 'include'
        this.direction = opts.containsKey('direction') ? (opts.direction as String) : 'both'
        this.kind = opts.kind as String
        this.order = opts.containsKey('order') ? (opts.order as Integer) : 100

        // Parse list fields (can be String or List)
        this.ulabelUids = ConfigUtils.parseStringOrList(opts.ulabel_uids)
        this.projectUids = ConfigUtils.parseStringOrList(opts.project_uids)

        // Validate configuration
        validate()

        // Compile pattern
        this.compiledPattern = ConfigUtils.compilePattern(this.pattern, 'pattern')
    }

    /**
     * Validate the rule configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    private void validate() {
        if (!this.pattern?.trim()) {
            throw new IllegalArgumentException("Rule pattern is required")
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
        if (!enabled) {
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
     * Check if this is an include rule
     * @return true if type is 'include'
     */
    boolean isIncludeRule() {
        return this.type == 'include'
    }

    /**
     * Check if this is an exclude rule
     * @return true if type is 'exclude'
     */
    boolean isExcludeRule() {
        return this.type == 'exclude'
    }

    @Override
    String toString() {
        return "ArtifactRule{" +
            "enabled=${enabled}, " +
            "pattern='${pattern}', " +
            "type='${type}', " +
            "direction='${direction}', " +
            "kind='${kind}', " +
            "ulabelUids=${ulabelUids}, " +
            "projectUids=${projectUids}, " +
            "order=${order}" +
            "}"
    }
}
