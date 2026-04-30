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
import nextflow.trace.event.WorkflowOutputEvent

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

    /**
     * Called once when the Nextflow session is created, before any process runs.
     *
     * Initialises the {@link LaminRunManager} with the session (which provides workflow
     * metadata, parameters, config, and the output directory) and pre-creates the
     * Transform and Run records in LaminDB so that a Run UID is available for the
     * rest of the workflow.
     */
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

    /**
     * Called when the workflow execution starts (after processes and channels are set up).
     *
     * Marks the Run as started in LaminDB and processes any explicitly configured input
     * artifact paths (from {@code lamin.artifacts} or {@code lamin.input_artifacts} config),
     * creating input Artifact records and linking them to the Run.
     */
    @Override
    void onFlowBegin() {
        log.debug "LaminObserver.onFlowBegin"
        state.startRun()
        state.processConfigPaths('input')
    }

    /**
     * Called each time a file is published to a {@code publishDir} destination.
     *
     * {@code event.target} is the final published path (local or cloud). {@code event.labels}
     * are the strings declared with the {@code label} directive on the output's publish block.
     *
     * <p>Two cases are handled:</p>
     * <ul>
     *   <li><strong>Normal content file</strong>: creates an output Artifact for the target
     *       path and links any publishDir labels as ULabels (if the
     *       {@code features.publish_dir_labels} flag is enabled).</li>
     *   <li><strong>Index/manifest file</strong> (written after {@code onWorkflowOutput}):
     *       the Artifact was already created by {@link #onWorkflowOutput}; only the labels
     *       are linked to the existing artifact.</li>
     * </ul>
     */
    @Override
    void onFilePublish(FilePublishEvent event) {
        log.debug "LaminObserver.onFilePublish: ${event.source} -> ${event.target}"
        state.createOutputArtifactOnFilePublish(event.target, event.labels)
    }

    /**
     * Called once per named workflow output block after all its channel values have been published.
     *
     * {@code event.name} is the output block name (e.g. {@code 'bam_files'}). {@code event.value}
     * is the fully published value: a single Path, a list of Paths, or a structured map.
     * {@code event.index} is the optional manifest file path (CSV/JSON/YAML) that Nextflow
     * writes to summarise all published records; it is non-null only when the output block
     * contains an {@code index} directive.
     *
     * <p>For content files ({@code event.value}) this hook fires <em>after</em> the individual
     * {@link #onFilePublish} calls, so the artifacts already exist in the cache and
     * {@link LaminRunManager#createOutputArtifactOnWorkflowOutput} is a no-op for those paths.</p>
     *
     * <p>For index/manifest files ({@code event.index}) this hook fires <em>before</em> the
     * corresponding {@link #onFilePublish}, so the artifact is created here with the output
     * name in its description; the subsequent {@code onFilePublish} then attaches labels.</p>
     */
    @Override
    void onWorkflowOutput(WorkflowOutputEvent event) {
        log.debug "LaminObserver.onWorkflowOutput: ${event.name} = ${event.value}"
        if (event.value instanceof Path) {
            state.createOutputArtifactOnWorkflowOutput((Path) event.value, event.name)
        } else if (event.value instanceof Collection) {
            for (Object item : (Collection) event.value) {
                if (item instanceof Path) {
                    state.createOutputArtifactOnWorkflowOutput((Path) item, event.name)
                }
            }
        }
        if (event.index != null) {
            state.createOutputArtifactOnWorkflowOutput(event.index, event.name)
        }
    }

    /**
     * Called each time a process task finishes successfully.
     *
     * Inspects the task's staged input files and creates an input Artifact record for each
     * source path (the original file before staging). This captures which files were consumed
     * as inputs, linking them to the Run for lineage tracking. Paths inside the Nextflow work
     * directory and other excluded locations are filtered out by {@link LaminRunManager}.
     */
    @Override
    void onTaskComplete(TaskEvent event) {
        TaskRun task = event.handler.task
        List<FileHolder> inputFiles = task.getInputFiles()

        log.debug "LaminObserver.onTaskComplete: ${task.name} with inputFiles: ${inputFiles}"

        for (FileHolder holder : inputFiles) {
            Path source = holder.getSourcePath()
            log.debug "LaminObserver.onTaskComplete ${task.name}: '${source.toUri()}' staged as '${holder.getStageName()}'"
            state.createInputArtifact(source)
        }
    }

    /**
     * Called when the workflow finishes successfully.
     *
     * Delegates to {@link #finalizeRunOnce()} which processes any explicitly configured output
     * artifact paths and then updates the Run record in LaminDB with the finish time and
     * {@code COMPLETED} status.
     */
    @Override
    void onFlowComplete() {
        log.debug "LaminObserver.onFlowComplete"
        finalizeRunOnce()
    }

    /**
     * Called when the workflow terminates due to a task failure or other error.
     *
     * Delegates to {@link #finalizeRunOnce()} which updates the Run record in LaminDB with
     * an {@code ERRORED} or {@code ABORTED} status. The guard in {@code finalizeRunOnce}
     * ensures that if both {@code onFlowError} and {@code onFlowComplete} fire (which can
     * happen on cancellation), the Run is only finalised once.
     */
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
        state.processConfigPaths('output')
        state.finalizeRun()
    }
}
