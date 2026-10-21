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

package ai.lamin.nf_lamin.model

import spock.lang.Specification

class ArtifactStatsTest extends Specification {

    def 'toKwargs uses the lamindb field names and plain java types'() {
        when:
        Map<String, Object> kwargs = new ArtifactStats(3L, 'kAFQmDzST7DWlj99KOF_cg', 'md5', null).toKwargs()

        then:
        kwargs.keySet() as List == ['size', 'hash', 'hash_type', 'n_files']
        kwargs.size instanceof Long
        kwargs.hash instanceof String
        kwargs.hash_type instanceof String
        kwargs.n_files == null
    }

    def 'toKwargs carries n_files for a directory'() {
        expect:
        new ArtifactStats(2L, 'YNCwyFw7rBegqZLBVEckCg', 'md5-d', 2L).toKwargs().n_files == 2L
    }

    def 'hasHash needs both a hash and a hash type'() {
        expect:
        new ArtifactStats(1L, 'DMF1ucDxtqgxw5niaXcmYQ', 'md5', null).hasHash()
        !new ArtifactStats(0L, null, null, 0L).hasHash()
        !new ArtifactStats(1L, 'DMF1ucDxtqgxw5niaXcmYQ', null, null).hasHash()
    }
}
