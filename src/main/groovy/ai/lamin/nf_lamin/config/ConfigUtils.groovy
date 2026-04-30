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
import java.util.regex.Pattern

/**
 * Utility methods for parsing configuration values.
 */
@CompileStatic
class ConfigUtils {

    /**
     * Parse a value that can be either a String or a List of Strings into a List.
     * Normalizes list inputs by converting each element to string and filtering null/empty values.
     * @param value The value to parse (String, List, or null)
     * @return List of strings (empty list if null or invalid type)
     */
    static List<String> parseStringOrList(Object value) {
        if (value == null) {
            return []
        }
        if (value instanceof String) {
            return value ? [value as String] : []
        }
        if (value instanceof List) {
            // Normalize list: convert each element to string and filter null/empty values
            return (value as List).collect { it?.toString() }.findAll { it } as List<String>
        }
        return []
    }

    /**
     * Invoke a closure whose body may reference {@code params} (e.g. {@code { params.outdir }}).
     * Sets the closure delegate to {@code [params: workflowParams]} with DELEGATE_FIRST
     * resolution so that bare {@code params} references resolve correctly, matching how
     * Nextflow evaluates closures in config files.
     *
     * @param closure The closure to invoke
     * @param workflowParams Nextflow workflow params (session.params)
     * @return The value returned by the closure
     */
    static Object evalClosureWithParams(Closure closure, Map workflowParams) {
        closure.delegate = [params: workflowParams ?: [:]]
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        return closure.call()
    }

    /**
     * Resolve a description from a config value (String or Closure) with a context map.
     *
     * The Closure receives a binding with the provided context variables
     * (e.g. {@code runId}, {@code path}, {@code outputName}) via
     * {@code DELEGATE_FIRST} resolution, matching how Nextflow evaluates closures.
     * Additional context variables may be added in the future without breaking existing closures.
     *
     * @param descConfig The description config value (String, Closure, or null)
     * @param context Map of context variables exposed to the closure
     * @return The resolved description string, or null if descConfig is null
     */
    static String resolveDescription(Object descConfig, Map<String, Object> context) {
        if (descConfig == null) {
            return null
        }
        if (descConfig instanceof Closure) {
            Closure cl = (Closure) descConfig
            cl.delegate = context
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            Object result = cl.call()
            return result?.toString()
        }
        return descConfig.toString()
    }

    /**
     * Compile a regex pattern string into a Pattern object.
     * @param pattern The pattern string to compile (may be null)
     * @param fieldName The field name for error messages
     * @return Compiled Pattern or null if pattern is null/empty
     * @throws IllegalArgumentException if pattern is invalid
     */
    static Pattern compilePattern(String pattern, String fieldName) {
        if (!pattern) {
            return null
        }
        try {
            return Pattern.compile(pattern)
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ${fieldName} regex '${pattern}': ${e.message}")
        }
    }
}
