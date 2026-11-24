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

import java.lang.reflect.Field

import nextflow.cli.Launcher
import nextflow.cli.PluginExecAware
import nextflow.file.FileHelper
import nextflow.plugin.BasePlugin
import org.pf4j.PluginWrapper

import ai.lamin.nf_lamin.cli.LaminCmdEntry
import ai.lamin.nf_lamin.nio.LaminFileSystemProvider
import ai.lamin.nf_lamin.nio.LaminS3FileSystemProvider

/**
 * The plugin entry point.
 *
 * Registers the lamin:// FileSystemProvider when the plugin starts.
 */
@Slf4j(category = 'nextflow.lamin.LaminPlugin')
@CompileStatic
class LaminPlugin extends BasePlugin implements PluginExecAware {

    LaminPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }

    @Override
    void start() {
        super.start()
        // Register the Lamin file system provider
        FileHelper.getOrInstallProvider(LaminFileSystemProvider)
        log.debug "Registered LaminFileSystemProvider for lamin:// URIs"
        turnOffGlobbing(LaminS3FileSystemProvider.SCHEME)
    }

    /**
     * Make Nextflow's {@code file()} treat paths of a scheme literally, as it does for http.
     *
     * A {@code lamin://} publish target resolves to a {@code lamin-s3} path, and {@code file()}
     * would otherwise read the {@code ?} of its query string as a glob wildcard. Nextflow keeps
     * these schemes in a private list, so this degrades to a no-op if that list changes.
     */
    static void turnOffGlobbing(String scheme) {
        try {
            Field field = FileHelper.getDeclaredField('UNSUPPORTED_GLOB_WILDCARDS')
            field.setAccessible(true)
            List<String> schemes = (List<String>) field.get(null)
            if (!schemes.contains(scheme)) {
                field.set(null, schemes + [scheme])
            }
        }
        catch (Exception e) {
            log.debug "Cannot turn off globbing for ${scheme}:// paths: ${e.message}"
        }
    }

    @Override
    int exec(Launcher launcher, String pluginId, String cmd, List<String> args) {
        return new LaminCmdEntry().exec(launcher, pluginId, cmd, args)
    }
}
