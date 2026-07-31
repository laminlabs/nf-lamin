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
import java.lang.reflect.Field

/**
 * Reads the Seqera Platform watch URL from the Nextflow session.
 *
 * Nextflow 26.04 exposes {@code workflow.platform}
 * (<a href="https://github.com/nextflow-io/nextflow/pull/6545">nextflow-io/nextflow#6545</a>),
 * which nf-tower fills with the watch URL. On 25.10, the minimum supported version, it is only
 * held in a private field of the nf-tower observer. Both are read reflectively, and anything
 * unexpected yields no reference rather than an error.
 */
@Slf4j
@CompileStatic
class SeqeraPlatformHelper {

    /** Session fields holding the trace observers, newest Nextflow first. */
    private static final List<String> OBSERVER_FIELDS = ['observersV2', 'observers']

    /** The nf-tower observer holding the watch URL; renamed to TowerObserver in 26.04. */
    private static final List<String> TOWER_OBSERVERS = ['TowerClient', 'TowerObserver']

    private SeqeraPlatformHelper() {
    }

    /**
     * @param session The current Nextflow session
     * @return the watch URL, or null if the run is not executing against Seqera Platform
     */
    static String resolveRunReference(Session session) {
        if (session == null) {
            return null
        }
        String reference = readReference(session.getWorkflowMetadata())
        return reference ?: readReferenceFromObservers(session)
    }

    /**
     * Read the watch URL from the nf-tower observer, for Nextflow versions without
     * {@code workflow.platform}. Takes an Object so it can be tested with a stand-in.
     *
     * @param session The Nextflow session
     * @return the watch URL, or null if unavailable
     */
    static String readReferenceFromObservers(Object session) {
        if (session == null) {
            return null
        }
        for (Object observer : findObservers(session)) {
            if (observer == null || !TOWER_OBSERVERS.contains(observer.getClass().simpleName)) {
                continue
            }
            Object url = readField(observer, 'watchUrl')
            String reference = url?.toString()?.trim()
            if (reference) {
                log.debug "Read the Seqera Platform watch URL from ${observer.getClass().simpleName}"
                return reference
            }
        }
        return null
    }

    /**
     * @param session The Nextflow session
     * @return the trace observers, or an empty list if they cannot be read
     */
    private static Collection<?> findObservers(Object session) {
        for (String fieldName : OBSERVER_FIELDS) {
            Object observers = readField(session, fieldName)
            if (observers instanceof Collection) {
                return (Collection) observers
            }
        }
        log.debug "Could not find the trace observers on ${session.getClass().simpleName}"
        return []
    }

    /**
     * Read a field by name, walking up the class hierarchy, whatever its visibility.
     *
     * @param target    The object to read from
     * @param fieldName The field to read
     * @return the value, or null if the field does not exist or cannot be read
     */
    private static Object readField(Object target, String fieldName) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            Field field
            try {
                field = type.getDeclaredField(fieldName)
            }
            catch (NoSuchFieldException e) {
                continue
            }
            try {
                field.setAccessible(true)
                return field.get(target)
            }
            catch (Exception e) {
                log.debug "Could not read ${type.simpleName}.${fieldName}: ${e.message}"
                return null
            }
        }
        return null
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
