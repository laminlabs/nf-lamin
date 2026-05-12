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

package ai.lamin.nf_lamin.util

import groovy.transform.CompileStatic

@CompileStatic
class MaskingUtils {

    /**
     * Masks a sensitive value. If the number of characters is at least 12, shows
     * the first 2 and last 2 characters with '****' in between; otherwise fully redacts.
     *
     * @param value The sensitive value to mask
     * @return The masked string
     */
    static String maskValue(String value) {
        if (value == null) return '****'
        if (value.length() >= 12) {
            return "${value[0..1]}****${value[-2..-1]}"
        }
        return '****'
    }
}
