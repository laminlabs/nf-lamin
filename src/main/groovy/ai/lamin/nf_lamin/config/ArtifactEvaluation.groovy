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
 * Contains the tracking decision and the accumulated metadata from artifact
 * config and all matching rules (ulabel UIDs, kind).
 */
@CompileStatic
class ArtifactEvaluation {

    /**
     * Whether the artifact should be tracked
     */
    final boolean shouldTrack

    /**
     * Accumulated ULabel UIDs from artifact config and matching rules.
     */
    final List<String> ulabelUids

    /**
     * Artifact kind (e.g., 'dataset', 'model'), or null if not specified.
     */
    final String kind

    /**
     * Artifact key, or null if not specified.
     */
    final String key

    /**
     * Create a new ArtifactEvaluation with typed fields.
     *
     * @param shouldTrack Whether the artifact should be tracked
     * @param ulabelUids Accumulated ULabel UIDs
     * @param kind Artifact kind, or null
     * @param key Artifact key, or null
     */
    ArtifactEvaluation(boolean shouldTrack, List<String> ulabelUids, String kind, String key = null) {
        this.shouldTrack = shouldTrack
        this.ulabelUids = ulabelUids ?: []
        this.kind = kind
        this.key = key
    }

    /**
     * Factory for a "not tracked" result with empty metadata.
     */
    static ArtifactEvaluation notTracked() {
        return new ArtifactEvaluation(false, [], null, null)
    }

    /**
     * Get artifact key
     * @return Artifact key or null
     */
    String getKey() {
        return this.key
    }

    @Override
    String toString() {
        return "ArtifactEvaluation{shouldTrack=${shouldTrack}, ulabelUids=${ulabelUids}, kind=${kind}, key=${key}}"
    }
}
