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

package nextflow.lamin

import java.nio.file.Path
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.processor.TaskHandler
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord

/**
 * Example workflow events observer
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
@Slf4j
@CompileStatic
class LaminObserver implements TraceObserver {
    void logInfo(String message) {
        log.info("nf-lamin> " + message)
    }
    
    @Override
    void onFlowCreate(Session session) {
        logInfo "onFlowCreate runName='${session.runName}', manifest=${session.config.manifest}"
        logInfo "  session.binding: ${session.binding}"
        def configKeys = session.config.collect{k, v -> k}
        for (key in configKeys) {
            logInfo "  session.config.$key: ${session.config[key]}"
        }
        logInfo "  session.cacheable: ${session.cacheable}"
        logInfo "  session.resumeMode: ${session.resumeMode}"
        logInfo "  session.outputDir: ${session.outputDir}"
        logInfo "  session.workDir: ${session.workDir}"
        logInfo "  session.bucketDir: ${session.bucketDir}"
        logInfo "  session.baseDir: ${session.baseDir}"
        logInfo "  session.scriptName: ${session.scriptName}"
        logInfo "  session.script: ${session.script}"
        logInfo "  session.runName: ${session.runName}"
        logInfo "  session.stubRun: ${session.stubRun}"
        logInfo "  session.preview: ${session.preview}"
        logInfo "  session.profile: ${session.profile}"
        logInfo "  session.commandLine: ${session.commandLine}"
        logInfo "  session.commitId: ${session.commitId}"
    }

    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace) {
        logInfo "onProcessComplete name='${handler.task.name}'"
    }

    @Override
    void onProcessCached(TaskHandler handler, TraceRecord trace) {
        logInfo "onProcessCached name='${handler.task.name}'"
    }

    @Override
    void onFilePublish(Path destination, Path source) {
        logInfo "onFilePublish source=${source.toUriString()}, destination=${destination.toUriString()}"
    }
    
    @Override
    void onFlowError(TaskHandler handler, TraceRecord trace) {
        logInfo "onFlowError name='${handler.task.name}'"
    }

    @Override
    void onFlowComplete() {
        logInfo "onFlowComplete"
    }
}
