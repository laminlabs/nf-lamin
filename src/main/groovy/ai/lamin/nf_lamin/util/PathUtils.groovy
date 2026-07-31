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
import nextflow.file.FileHelper
import java.nio.file.Path

/**
 * Utility methods for resolving paths handed to the plugin by a workflow.
 */
@Slf4j
@CompileStatic
class PathUtils {

    /**
     * Resolve a value a workflow passed to a plugin function to the paths it refers to.
     *
     * Remote paths are resolved with Nextflow's {@code FileHelper.asPath}, so a
     * {@code s3://} or {@code lamin://} string yields a path on the matching file system.
     *
     * @param value    A Path, a path String, or a collection of either
     * @param fnName   The function name for error messages
     * @return the resolved paths
     * @throws IllegalArgumentException if the value is null or of an unsupported type
     */
    static List<Path> toPaths(Object value, String fnName) {
        if (value == null) {
            throw new IllegalArgumentException("${fnName}: no file given (value is null)")
        }
        if (value instanceof Path) {
            return [(Path) value]
        }
        if (value instanceof CharSequence) {
            return [FileHelper.asPath(value.toString())]
        }
        if (value instanceof Collection) {
            List<Path> paths = []
            for (Object item : (Collection) value) {
                paths.addAll(toPaths(item, fnName))
            }
            return paths
        }
        throw new IllegalArgumentException(
            "${fnName}: cannot resolve a ${value.getClass().simpleName} to a file, " +
            'expected a file, a file path, or a collection of either'
        )
    }

    /**
     * Render a path as a URI string that two references to the same file agree on.
     *
     * Absolutises and normalises the path so that a relative path matches the absolute one,
     * and renders remote protocols consistently because some providers print them as
     * {@code s3:/} or {@code s3:///}.
     *
     * @param path The path to render
     * @return the URI string, or null if the path is null
     */
    static String toUriKey(Path path) {
        if (path == null) {
            return null
        }
        URI uri
        try {
            uri = path.toAbsolutePath().normalize().toUri()
        } catch (Exception e) {
            log.debug "Could not absolutise ${path}: ${e.message}"
            uri = path.toUri()
        }

        String uriStr = uri.toString()
        String scheme = uri.getScheme()
        // Leave local paths alone: 'file' URIs have no authority, so their leading slashes
        // are part of the path and collapsing them would mangle the key
        if (scheme == null || scheme == 'file') {
            return uriStr
        }
        String rest = uriStr.substring(scheme.length() + 1)
        int slashes = 0
        while (slashes < rest.length() && rest.charAt(slashes) == ('/' as char)) {
            slashes++
        }
        return scheme + '://' + rest.substring(slashes)
    }
}
