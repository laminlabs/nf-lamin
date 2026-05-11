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

import spock.lang.Specification
import spock.lang.Unroll

class MaskingUtilsTest extends Specification {

    // ========== maskValue tests ==========

    def 'maskValue returns *** for null'() {
        expect:
        MaskingUtils.maskValue(null) == '***'
    }

    def 'maskValue returns *** for empty string'() {
        expect:
        MaskingUtils.maskValue('') == '***'
    }

    @Unroll
    def 'maskValue returns *** for short value "#value" (length #value.length())'() {
        expect:
        MaskingUtils.maskValue(value) == '***'

        where:
        value << ['a', 'ab', 'abc', 'abcd', 'abcde', 'abcdefghijk']  // up to length 11
    }

    def 'maskValue shows first 2 and last 2 chars for value of exactly 12 characters'() {
        expect:
        MaskingUtils.maskValue('abcdefghijkl') == 'ab***kl'
    }

    def 'maskValue shows first 2 and last 2 chars for long value'() {
        expect:
        MaskingUtils.maskValue('ABCDEFGHIJKLMNOPQRSTUVWXYZ') == 'AB***YZ'
    }
}
