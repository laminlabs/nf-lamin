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

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Size, hash and file count of an artifact, computed the way lamindb computes them.
 *
 * {@code hashType} tells how {@code hash} was derived: {@code md5}, {@code sha1-fl},
 * {@code md5-d} for directories, or {@code md5-<N>} for S3 multipart uploads.
 * {@code nFiles} is null for file-like artifacts.
 */
@CompileStatic
@EqualsAndHashCode
@ToString(includeNames = true)
final class ArtifactStats {

    final Long size
    final String hash
    final String hashType
    final Long nFiles

    ArtifactStats(Long size, String hash, String hashType, Long nFiles) {
        this.size = size
        this.hash = hash
        this.hashType = hashType
        this.nFiles = nFiles
    }

    boolean hasHash() {
        return hash != null && hashType != null
    }

    /**
     * The {@code stats} keyword argument of {@code ln.Artifact()}.
     */
    Map<String, Object> toKwargs() {
        Map<String, Object> kwargs = new LinkedHashMap<>()
        kwargs.put('size', size)
        kwargs.put('hash', hash)
        kwargs.put('hash_type', hashType)
        kwargs.put('n_files', nFiles)
        return kwargs
    }
}
