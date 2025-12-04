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
import nextflow.file.FileHolder
import nextflow.processor.TaskRun
import nextflow.trace.TraceObserverV2
import nextflow.trace.event.TaskEvent
import nextflow.trace.event.FilePublishEvent

import ai.lamin.nf_lamin.model.RunStatus

/**
 * Implements workflow events observer for Lamin provenance tracking
 *
 * This observer tracks workflow execution events and creates corresponding
 * records in Lamin for provenance and metadata management.
 */
@Slf4j
@CompileStatic
class LaminObserver implements TraceObserverV2 {

    private final LaminRunManager state = LaminRunManager.instance
    private volatile boolean runFinalized = false

    @Override
    void onFlowCreate(Session session) {
        log.debug "LaminObserver.onFlowCreate"
        try {
            state.initializeRunManager(session)
            state.initializeRun()
            runFinalized = false
        } catch (Exception e) {
            log.error "Could not initialize Lamin run: ${e.message}"
            throw e
        }
    }

    @Override
    void onFlowBegin() {
        log.debug "LaminObserver.onFlowBegin"
        state.startRun()
    }

    @Override
    void onFilePublish(FilePublishEvent event) {
        log.debug "LaminObserver.onFilePublish: ${event.source} -> ${event.target}"
        state.createOutputArtifact(event.target)
    }

    @Override
    void onTaskComplete(TaskEvent event) {
        TaskRun task = event.handler.task
        List<FileHolder> inputFiles = task.getInputFiles()

        log.debug "LaminObserver.onTaskComplete: ${task.name} with inputFiles: ${inputFiles}"

        for (FileHolder holder : inputFiles) {
            Path source = holder.getSourcePath()
            log.info "LaminObserver.onTaskComplete ${task.name}: '${source.toUri()}' staged as '${holder.getStageName()}'"
            state.createInputArtifact(source)
        }
    }

    @Override
    void onFlowComplete() {
        log.debug "LaminObserver.onFlowComplete"
        finalizeRunOnce()
    }

    @Override
    void onFlowError(TaskEvent event) {
        log.debug "LaminObserver.onFlowError"
        finalizeRunOnce()
    }

    private synchronized void finalizeRunOnce() {
        if (runFinalized) {
            log.debug "Run already finalized, skipping duplicate finalization"
            return
        }
        runFinalized = true
        state.finalizeRun()
    }
}
