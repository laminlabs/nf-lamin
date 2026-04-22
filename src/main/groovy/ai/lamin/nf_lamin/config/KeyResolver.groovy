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

package ai.lamin.nf_lamin.config

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Resolves artifact keys from configuration values (String templates or Closures).
 *
 * <p>The {@code key} config option accepts three types:</p>
 * <ul>
 *   <li><strong>String template</strong> with variables:
 *     <ul>
 *       <li>{@code {basename}} — full filename with extension (e.g. {@code report.html})</li>
 *       <li>{@code {filename}} — filename without extension (e.g. {@code report})</li>
 *       <li>{@code {ext}} — extension including dot (e.g. {@code .html})</li>
 *       <li>{@code {parent}} — immediate parent directory name</li>
 *       <li>{@code {parent.parent}} — grandparent directory, and so on</li>
 *     </ul>
 *   </li>
 *   <li><strong>Closure</strong> that receives a {@link Path} and returns the
 *       artifact key (String). Example: {@code key = { path -> path.fileName.toString() }}</li>
 *    <li><strong>Map shorthand</strong>: {@code [relativize: '/path/to/outdir']} — strips the
 *       given directory prefix from the path. Equivalent to writing the relativize closure
 *       manually. Falls back to basename on failure.</li>
 * </ul>
 */
@Slf4j
@CompileStatic
class KeyResolver {

    /**
     * Pattern matching {parent}, {parent.parent}, {parent.parent.parent}, etc.
     */
    private static final Pattern PARENT_PATTERN = Pattern.compile('\\{parent(\\.parent)*\\}')

    /**
     * Resolve a key from a config value that can be either a String template, Closure, or Map.
     *
     * @param keyConfig The key configuration value (String template, Closure, or Map)
     * @param path The Path object for the artifact file
     * @return The resolved key string, or null if keyConfig is null
     */
    static String resolveKey(Object keyConfig, Path path) {
        if (keyConfig == null) {
            return null
        }

        String pathStr = path.toUri().toString()

        if (keyConfig instanceof Map) {
            return resolveMapConfig((Map) keyConfig, pathStr)
        }

        if (keyConfig instanceof Closure) {
            return invokeClosure((Closure) keyConfig, path)
        }

        return resolveStringTemplate(keyConfig as String, pathStr)
    }

    /**
     * Derive the default key from a path when no key config is set.
     * Returns the basename (full filename with extension) of the path.
     *
     * @param path The full file path (URI string or filesystem path)
     * @return The basename, or null if the path is null/empty
     */
    static String defaultKeyFromPath(String path) {
        if (!path) {
            return null
        }
        return extractBasename(path)
    }

    /**
     * Resolve a Map-based key config.
     *
     * <p>Supports: {@code [relativize: params.outdir]} — strips the given directory
     * prefix from the artifact path, preserving subdirectory structure as the key.</p>
     *
     * <p>Uses {@link URI#relativize} which works uniformly across all URI schemes
     * ({@code file://}, {@code s3://}, {@code gs://}, {@code az://}, etc.). Plain
     * local paths (no scheme) are normalised to absolute {@code file://} URIs before
     * relativizing. Falls back to the basename if relativization fails or if the
     * artifact path is not under the configured base directory.</p>
     *
     * @param config The map config
     * @param pathStr The full file path as a URI string
     * @return The resolved key, or the basename on failure
     */
    private static String resolveMapConfig(Map config, String pathStr) {
        Object relativizeValue = config.get('relativize')
        if (relativizeValue != null) {
            Object resolvedValue = (relativizeValue instanceof Closure) ? ((Closure) relativizeValue).call() : relativizeValue
            String baseDir = resolvedValue?.toString() ?: ''
            if (!baseDir) {
                log.warn "Key map 'relativize' value is empty for path '${pathStr}', falling back to basename"
                return extractBasename(pathStr)
            }
            try {
                URI baseUri = dirToUri(baseDir)
                URI fileUri = URI.create(pathStr)
                URI relative = baseUri.relativize(fileUri)
                if (!relative.isAbsolute()) {
                    return relative.toString()
                }
            } catch (Exception e) {
                log.debug "Key map 'relativize' failed for '${pathStr}' with base '${baseDir}': ${e.message}"
            }
            log.warn "Key map 'relativize' could not relativize '${pathStr}' against '${baseDir}', falling back to basename"
            return extractBasename(pathStr)
        }
        log.warn "Key map config has no recognized keys for path '${pathStr}', falling back to basename"
        return extractBasename(pathStr)
    }

    /**
     * Convert a directory string to a {@link URI} ending with {@code /},
     * suitable for use with {@link URI#relativize}.
     *
     * <p>Strings that already contain {@code ://} (e.g. {@code s3://bucket/results},
     * {@code gs://bucket/results}, {@code file:///home/user/results}) are used as-is.
     * Plain local paths (no scheme) are resolved to absolute {@code file://} URIs via
     * {@link java.nio.file.Paths#get}.</p>
     *
     * @param dir The directory string (URI or local path)
     * @return A non-opaque {@link URI} with a trailing slash
     */
    private static URI dirToUri(String dir) {
        String uriStr = dir.contains('://') ? dir : Paths.get(dir).toAbsolutePath().toUri().toString()
        if (!uriStr.endsWith('/')) {
            uriStr = uriStr + '/'
        }
        return URI.create(uriStr)
    }

    /**
     * Invoke a Closure-based key resolver.
     *
     * @param closure The closure to invoke (receives Path, returns String)
     * @param path The Path object passed to the closure
     * @return The resolved key
     */
    private static String invokeClosure(Closure closure, Path path) {
        String pathStr = path.toUri().toString()
        try {
            Object result = closure.call(path)
            if (result == null) {
                log.warn "Key closure returned null for path '${pathStr}', falling back to basename"
                return extractBasename(pathStr)
            }
            return result.toString()
        } catch (Exception e) {
            log.error "Key closure failed for path '${pathStr}': ${e.message}"
            log.debug "Key closure exception", e
            return extractBasename(pathStr)
        }
    }

    /**
     * Resolve a String template key with variable substitution.
     *
     * @param template The template string
     * @param path The full file path
     * @return The resolved key
     */
    private static String resolveStringTemplate(String template, String path) {
        String result = template

        // Replace {parent}, {parent.parent}, etc.
        result = replaceParentVariables(result, path)

        // Replace simple variables
        String basename = extractBasename(path)
        String filename = extractFilename(basename)
        String ext = extractExtension(basename)

        result = result.replace('{basename}', basename ?: '')
        result = result.replace('{filename}', filename ?: '')
        result = result.replace('{ext}', ext ?: '')

        return result
    }

    /**
     * Replace all {parent}, {parent.parent}, etc. placeholders in a template.
     *
     * @param template The template string
     * @param path The full file path
     * @return Template with parent variables replaced
     */
    private static String replaceParentVariables(String template, String path) {
        Matcher matcher = PARENT_PATTERN.matcher(template)
        StringBuffer sb = new StringBuffer()
        while (matcher.find()) {
            String match = matcher.group()
            // Count the number of "parent" segments: {parent} = 1, {parent.parent} = 2, etc.
            int level = match.count('parent')
            String replacement = extractParentAtLevel(path, level)
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    /**
     * Extract the basename (full filename with extension) from a path or URI string.
     * For example: /path/to/report.html → report.html
     */
    static String extractBasename(String path) {
        if (!path) {
            return ''
        }
        // Strip trailing slashes
        String cleaned = path.replaceAll('/+$', '')
        // Handle both URI and filesystem paths
        int lastSlash = cleaned.lastIndexOf('/')
        return lastSlash >= 0 ? cleaned.substring(lastSlash + 1) : cleaned
    }

    /**
     * Extract the filename (without extension) from a basename.
     * Handles compound extensions like .fastq.gz
     * For example: report.html → report, file.fastq.gz → file
     */
    static String extractFilename(String basename) {
        if (!basename) {
            return ''
        }
        int dotIndex = basename.indexOf('.')
        return dotIndex > 0 ? basename.substring(0, dotIndex) : basename
    }

    /**
     * Extract the extension (including dot) from a basename.
     * Handles compound extensions like .fastq.gz
     * For example: report.html → .html, file.fastq.gz → .fastq.gz
     */
    static String extractExtension(String basename) {
        if (!basename) {
            return ''
        }
        int dotIndex = basename.indexOf('.')
        return dotIndex > 0 ? basename.substring(dotIndex) : ''
    }

    /**
     * Extract a parent directory name at a given level above the file.
     * Level 1 = immediate parent, level 2 = grandparent, etc.
     *
     * @param path The full file path
     * @param level How many levels up (1 = parent, 2 = grandparent, etc.)
     * @return The directory name at that level, or empty string if not enough depth
     */
    static String extractParentAtLevel(String path, int level) {
        if (!path || level < 1) {
            return ''
        }
        // Strip trailing slashes and split into segments
        String cleaned = path.replaceAll('/+$', '')
        // Remove scheme (e.g., s3://) for splitting
        String pathPart = cleaned.replaceAll('^\\w+:/+', '')
        String[] segments = pathPart.split('/')

        // segments[-1] is the file, segments[-2] is parent (level 1), etc.
        int index = segments.length - 1 - level
        return index >= 0 ? segments[index] : ''
    }
}
