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

package ai.lamin.nf_lamin

import java.nio.file.Path
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.processor.TaskHandler
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord

import ai.lamin.nf_lamin.model.RunStatus

/**
 * Implements workflow events observer for Lamin provenance tracking
 *
 * This observer tracks workflow execution events and creates corresponding
 * records in Lamin for provenance and metadata management.
 */
@Slf4j
@CompileStatic
class LaminObserver implements TraceObserver {

    private final LaminRunManager state = LaminRunManager.instance

    @Override
    void onFlowCreate(Session session) {
        log.debug "LaminObserver.onFlowCreate"
        try {
            state.initializeRunManager(session)
            state.initializeRun()
        } catch (Exception e) {
            log.error "Failed to initialize Lamin run", e
            throw e
        }
    }

    @Override
    void onFlowBegin() {
        log.debug "LaminObserver.onFlowBegin"
        state.startRun()
    }

    @Override
    void onFilePublish(Path destination, Path source) {
        log.debug "LaminObserver.onFilePublish: ${source} -> ${destination}"
        state.createOutputArtifact(destination)
    }

    @Override
    void onFlowComplete() {
        log.debug "LaminObserver.onFlowComplete"
        state.finalizeRun(RunStatus.COMPLETED)
    }

    @Override
    void onFlowError(TaskHandler handler, TraceRecord trace) {
        log.debug "LaminObserver.onFlowError"
        state.finalizeRun(RunStatus.ERRORED)
    }
}
