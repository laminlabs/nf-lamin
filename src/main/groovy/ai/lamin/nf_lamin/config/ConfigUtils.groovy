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
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
@CompileStatic
class ConfigUtils {

    /**
     * Parse a value that can be either a String or a List of Strings into a List.
     * @param value The value to parse (String, List, or null)
     * @return List of strings (empty list if null or invalid type)
     */
    static List<String> parseStringOrList(Object value) {
        if (value == null) {
            return []
        }
        if (value instanceof String) {
            return [value as String]
        }
        if (value instanceof List) {
            return value as List<String>
        }
        return []
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
