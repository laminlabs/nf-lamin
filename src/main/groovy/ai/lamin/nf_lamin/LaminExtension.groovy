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
import nextflow.Session
import nextflow.plugin.extension.Function
import nextflow.plugin.extension.PluginExtensionPoint
import java.nio.file.Path
import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.instance.Instance
import ai.lamin.nf_lamin.instance.InstanceSettings

/**
 * Implements a custom function which can be imported by
 * Nextflow scripts.
 */
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

    @Function
    Path getArtifactUrlByUid(String instanceOwner, String instanceName, String artifactUid) {
        LaminHub hub = LaminRunManager.instance.getHub()
        LaminConfig config = LaminRunManager.instance.getConfig()
        InstanceSettings instanceSettings = hub.getInstanceSettings(instanceOwner, instanceName)
        Instance instance = new Instance(hub, instanceSettings, config.getMaxRetries(), config.getRetryDelay())
        return instance.getArtifactUrlByUid(artifactUid)
    }

}
