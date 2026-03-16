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
import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Resolves artifact keys from configuration values (String templates or Closures).
 *
 * <p>The {@code key} config option accepts two types:</p>
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
     * Resolve a key from a config value that can be either a String template or a Closure.
     *
     * @param keyConfig The key configuration value (String template or Closure)
     * @param pathStr The full file path as a URI string (used for template resolution)
     * @param path The Path object (passed to Closures); may be null for template-only resolution
     * @return The resolved key string, or null if keyConfig is null
     */
    static String resolveKey(Object keyConfig, String pathStr, Path path) {
        if (keyConfig == null) {
            return null
        }

        if (keyConfig instanceof Closure) {
            return invokeClosure((Closure) keyConfig, pathStr, path)
        }

        String template = keyConfig as String
        return resolveStringTemplate(template, pathStr)
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
     * Invoke a Closure-based key resolver.
     *
     * @param closure The closure to invoke (receives Path, returns String)
     * @param pathStr The full file path as string (for fallback)
     * @param path The Path object passed to the closure
     * @return The resolved key
     */
    private static String invokeClosure(Closure closure, String pathStr, Path path) {
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
