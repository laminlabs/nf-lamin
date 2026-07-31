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
import groovy.util.logging.Slf4j
import nextflow.Session

/**
 * Reads the Seqera Platform watch URL from the Nextflow session.
 *
 * Nextflow 26.04 exposes {@code workflow.platform}
 * (<a href="https://github.com/nextflow-io/nextflow/pull/6545">nextflow-io/nextflow#6545</a>),
 * which nf-tower fills with the watch URL. Read reflectively because it does not exist on 25.10,
 * the minimum supported version.
 */
@Slf4j
@CompileStatic
class SeqeraPlatformHelper {

    /** Matches the {@code reference_type} of the existing Seqera runs on laminlabs/lamindata. */
    static final String REFERENCE_TYPE = 'Seqera'

    private SeqeraPlatformHelper() {
    }

    /**
     * @param session The current Nextflow session
     * @return the watch URL, or null if the run is not executing against Seqera Platform
     */
    static String resolveRunReference(Session session) {
        return session != null ? readReference(session.getWorkflowMetadata()) : null
    }

    /**
     * Takes an Object rather than a WorkflowMetadata so it can be tested without Nextflow 26.04.
     *
     * @param metadata The Nextflow workflow metadata
     * @return the watch URL, or null if unavailable
     */
    static String readReference(Object metadata) {
        if (metadata == null) {
            return null
        }
        try {
            Object platform = metadata.getClass().getMethod('getPlatform').invoke(metadata)
            if (platform == null) {
                return null
            }
            Object url = platform.getClass().getMethod('getWorkflowUrl').invoke(platform)
            // the API rejects GStrings
            return url?.toString()?.trim() ?: null
        }
        catch (NoSuchMethodException e) {
            log.debug 'Seqera Platform metadata is not available on this Nextflow version'
            return null
        }
        catch (Exception e) {
            log.debug "Could not read Seqera Platform metadata: ${e.message}"
            return null
        }
    }
}
