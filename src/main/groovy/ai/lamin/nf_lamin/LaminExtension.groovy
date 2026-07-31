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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.plugin.extension.Function
import nextflow.plugin.extension.PluginExtensionPoint
import java.nio.file.Path
import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.instance.Instance
import ai.lamin.nf_lamin.hub.InstanceSettings
import ai.lamin.nf_lamin.model.ArtifactAnnotation
import ai.lamin.nf_lamin.util.PathUtils

/**
 * Implements a custom function which can be imported by
 * Nextflow scripts.
 */
@Slf4j
@CompileStatic
class LaminExtension extends PluginExtensionPoint {

    @Override
    protected void init(Session session) {
    }

    /**
     * Returns the UID of the current Lamin run.
     *
     * @return the run UID or {@code null} if unavailable
     */
    @Function
    String getRunUid() {
        Map<String, Object> run = LaminRunManager.instance.run
        return run != null ? run.get('uid') as String : null
    }

    /**
     * Returns the UID of the current Lamin transform.
     *
     * @return the transform UID or {@code null} if unavailable
     */
    @Function
    String getTransformUid() {
        Map<String, Object> transform = LaminRunManager.instance.transform
        return transform != null ? transform.get('uid') as String : null
    }

    /**
     * Returns the currently configured LaminDB instance identifier.
     *
     * This function returns the instance slug in the format "owner/name" (e.g., "laminlabs/lamindata")
     * as configured in the lamin config block.
     *
     * @return the instance slug (e.g., "laminlabs/lamindata") or {@code null} if not available
     */
    @Function
    String getInstanceSlug() {
        return LaminRunManager.instance.getInstanceSlug()
    }

    /**
     * Attaches metadata to the artifact that Lamin registers for a file.
     *
     * The file keeps being published by Nextflow as usual; this only records what should be
     * attached to the resulting artifact. It can therefore be called before or after the file
     * is published, and repeated calls for the same file accumulate.
     *
     * <pre>
     * ch_out | map { f -&gt; annotateArtifact(f, kind: 'dataset', ulabel_uids: ['+qc-passed']) }
     * </pre>
     *
     * Does nothing when no Lamin instance is configured, so a workflow using it still runs
     * without the plugin being connected.
     *
     * @param opts   Named arguments: {@code kind}, {@code description}, {@code ulabel_uids},
     *               {@code project_uids}
     * @param target A file, a file path as a String, or a collection of either
     * @return {@code target}, unchanged, so the call can be the body of a {@code map} closure
     * @throws IllegalArgumentException if an option or the target type is not supported
     */
    @Function
    Object annotateArtifact(Map opts, Object target) {
        ArtifactAnnotation annotation = ArtifactAnnotation.fromMap(opts)
        for (Path path : PathUtils.toPaths(target, 'annotateArtifact')) {
            LaminRunManager.instance.registerAnnotation(path, annotation)
        }
        return target
    }

    /**
     * Attaches metadata to the artifact of a file, without any named arguments.
     *
     * @see #annotateArtifact(Map, Object)
     */
    @Function
    Object annotateArtifact(Object target) {
        return annotateArtifact([:], target)
    }

}
