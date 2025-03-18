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
    private Session session

    void logInfo(String message) {
        log.info("nf-lamin> " + message)
    }
    

/*
ln.Transform(
  key="nf-core/scrna-seq",
  version="2.7.1",
  type="pipeline",
  reference="https://github.com/nf-core/scrnaseq",
  reference_type="url",
)

ln.Run(
  transform=trafo,
  name="romantic_hawking"
  started_at=started_at_datetime,
)
*/

    @Override
    void onFlowCreate(Session session) {
        // store the session for later use
        this.session = session

        logInfo "onFlowCreate triggered!"

        def wfMetadata = session.getWorkflowMetadata()
        def key = wfMetadata.scriptFile.toString().replaceFirst("${wfMetadata.projectDir}/", "")

        // session.baseDir + "/" + session.scriptName

        def description = session.config.navigate("manifest.description") as String

        logInfo "Fetch or create Transform object:\n" +
            "  trafo = ln.Transform(\n" +
            "    key=\"${key}\",\n" +
            "    version=\"${wfMetadata.revision}\",\n" +
            "    type=\"pipeline\",\n" +
            "    reference=\"${wfMetadata.repository}\",\n" +
            "    reference_type=\"url\",\n" +
            "    description=\"${wfMetadata.manifest.getDescription()}\"\n" +
            "  )\n"

        logInfo "Create Run object:\n" +
            "  run = ln.Run(\n" +
            "    transform=trafo,\n" +
            "    name=\"${wfMetadata.runName}\",\n" +
            "    started_at=\"${wfMetadata.start}\"\n" +
            "  )\n"


    }

    void printWorkflowMetadata(nextflow.script.WorkflowMetadata wfMetadata) {
        logInfo "Printing wfMetadata"
        for (key in ["scriptId", "scriptFile", "scriptName", "repository", "commitId", "revision", "projectDir", "projectName", "start", "container", "commandLine", "nextflow", "outputDir", "workDir", "launchDir", "profile", "sessionId", "resume", "stubRun", "preview", "runName", "containerEngine", "configFiles", "stats", "userName", "homeDir", "manifest", "wave", "fusion", "failOnIgnore"]) {
            logInfo "  wfMetadata.$key: ${wfMetadata[key]}"
        }
    }

    void printSession(nextflow.Session session) {
        logInfo "  session.binding: ${session.binding}"
        def configKeys = session.config.collect{k, v -> k}
        for (key in configKeys) {
            logInfo "  session.config.$key: ${session.config[key]}"
        }
        for (key in ["cacheable", "resumeMode", "outputDir", "workDir", "bucketDir", "baseDir", "scriptName", "script", "runName", "stubRun", "preview", "profile", "commandLine", "commitId"]) {
            logInfo "  session.$key: ${session[key]}"
        }
    }

    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace) {
        // logInfo "onProcessComplete name='${handler.task.name}'"
    }

    @Override
    void onProcessCached(TaskHandler handler, TraceRecord trace) {
        // logInfo "onProcessCached name='${handler.task.name}'"
    }

    @Override
    void onFilePublish(Path destination, Path source) {
        logInfo "onFilePublish triggered!"
        logInfo "Create Artifact object:\n" +
            "  artifact = ln.Artifact(\n" +
            "    run=run,\n" +
            "    data=\"${destination.toUriString()}\",\n" +
            "  )\n"
    }
    
    @Override
    void onFlowError(TaskHandler handler, TraceRecord trace) {
        //logInfo "onFlowError name='${handler.task.name}'"
    }

    @Override
    void onFlowComplete() {
        logInfo "onFlowComplete triggered!"
        //this.session 
    }
}
