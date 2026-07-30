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

/**
 * Metadata a workflow asks to be attached to the artifact of a given path.
 *
 * Built from the named arguments of {@code annotateArtifact()} and applied by
 * {@link ai.lamin.nf_lamin.LaminRunManager} once the artifact for that path exists.
 */
@CompileStatic
class ArtifactAnnotation {

    /** Option names accepted by {@code annotateArtifact()}. */
    static final List<String> ACCEPTED_KEYS = ['kind', 'description', 'ulabel_uids', 'project_uids']

    /** Artifact kinds accepted by LaminDB, see {@code lamindb.base.types.ArtifactKind}. */
    static final List<String> VALID_KINDS = ['dataset', 'model', 'plan', '__lamindb_run__', '__lamindb_config__']

    /** Artifact kind, or null to leave it unchanged. */
    final String kind

    /** Artifact description, or null to leave it unchanged. */
    final String description

    /** ULabel UIDs or '?name'/'!name'/'+name' references to link. */
    final List<String> ulabelUids

    /** Project UIDs or '?name'/'!name'/'+name' references to link. */
    final List<String> projectUids

    ArtifactAnnotation(String kind, String description, List<String> ulabelUids, List<String> projectUids) {
        this.kind = kind
        this.description = description
        this.ulabelUids = ulabelUids ?: []
        this.projectUids = projectUids ?: []
    }

    /**
     * Build an annotation from the named arguments of {@code annotateArtifact()}.
     *
     * Rejects unknown options rather than dropping them, so that a typo surfaces as an error
     * in the workflow instead of as silently missing metadata in LaminDB.
     *
     * @param opts The named arguments (may be null or empty)
     * @return the parsed annotation
     * @throws IllegalArgumentException if an option is unknown or has an unusable value
     */
    static ArtifactAnnotation fromMap(Map opts) {
        if (!opts) {
            return new ArtifactAnnotation(null, null, null, null)
        }

        List<String> unknown = opts.keySet().collect { it as String } - ACCEPTED_KEYS
        if (unknown.contains('features')) {
            throw new IllegalArgumentException(
                "annotateArtifact: 'features' is not supported yet, see " +
                'https://github.com/laminlabs/nf-lamin/issues/102'
            )
        }
        if (unknown) {
            throw new IllegalArgumentException(
                "annotateArtifact: unknown option(s) ${unknown.join(', ')}. " +
                "Accepted options are: ${ACCEPTED_KEYS.join(', ')}"
            )
        }

        String kind = opts.get('kind') as String
        if (kind != null && !VALID_KINDS.contains(kind)) {
            throw new IllegalArgumentException(
                "annotateArtifact: invalid kind '${kind}'. Accepted kinds are: ${VALID_KINDS.join(', ')}"
            )
        }

        Object rawDescription = opts.get('description')
        String description = rawDescription != null ? rawDescription.toString() : null

        return new ArtifactAnnotation(
            kind,
            description,
            toUidList(opts.get('ulabel_uids'), 'ulabel_uids'),
            toUidList(opts.get('project_uids'), 'project_uids')
        )
    }

    /**
     * Normalise a UID option to a list of strings, accepting a bare value as a single entry.
     */
    private static List<String> toUidList(Object value, String optionName) {
        if (value == null) {
            return []
        }
        Collection<?> items = value instanceof Collection ? (Collection) value : [value]
        List<String> uids = []
        for (Object item : items) {
            if (item == null) {
                continue
            }
            String uid = item.toString().trim()
            if (!uid) {
                continue
            }
            uids.add(uid)
        }
        return uids
    }

    /**
     * Whether this annotation carries anything to apply.
     */
    boolean isEmpty() {
        return !kind && !description && !ulabelUids && !projectUids
    }

    @Override
    String toString() {
        List<String> parts = []
        if (kind) { parts.add("kind=${kind}" as String) }
        if (description) { parts.add("description='${description}'" as String) }
        if (ulabelUids) { parts.add("ulabel_uids=${ulabelUids}" as String) }
        if (projectUids) { parts.add("project_uids=${projectUids}" as String) }
        return "ArtifactAnnotation{${parts.join(', ')}}"
    }
}
