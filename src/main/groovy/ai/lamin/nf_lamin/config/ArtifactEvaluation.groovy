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

/**
 * Result of evaluating an artifact path against configuration rules.
 *
 * Contains both the tracking decision and the accumulated metadata
 * from global config and all matching rules.
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
@CompileStatic
class ArtifactEvaluation {

    /**
     * Whether the artifact should be tracked
     */
    final boolean shouldTrack

    /**
     * Accumulated metadata from global config and matching rules.
     * May contain: ulabel_uids, project_uids, kind
     */
    final Map<String, Object> metadata

    /**
     * Create a new ArtifactEvaluation
     * @param shouldTrack Whether the artifact should be tracked
     * @param metadata Accumulated metadata
     */
    ArtifactEvaluation(boolean shouldTrack, Map<String, Object> metadata) {
        this.shouldTrack = shouldTrack
        this.metadata = metadata ?: [:]
    }

    /**
     * Get ULabel UIDs from metadata
     * @return List of ULabel UIDs (may be empty)
     */
    List<String> getUlabelUids() {
        return (metadata.ulabel_uids as List<String>) ?: []
    }

    /**
     * Get Project UIDs from metadata
     * @return List of Project UIDs (may be empty)
     */
    List<String> getProjectUids() {
        return (metadata.project_uids as List<String>) ?: []
    }

    /**
     * Get artifact kind from metadata
     * @return Artifact kind or null
     */
    String getKind() {
        return metadata.kind as String
    }

    @Override
    String toString() {
        return "ArtifactEvaluation{shouldTrack=${shouldTrack}, metadata=${metadata}}"
    }
}
