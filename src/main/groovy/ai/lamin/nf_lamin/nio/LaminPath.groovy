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

package ai.lamin.nf_lamin.nio

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService

/**
 * Implements the Path interface for Lamin URIs.
 *
 * A LaminPath represents a virtual path in the format:
 * lamin://owner/instance/artifact/uid[/subpath]
 *
 * The path is "virtual" in that it references a LaminDB artifact which
 * is stored in underlying cloud storage (S3, GCS, etc.). The actual
 * file operations are delegated to the underlying storage provider.
 *
 * @author Lamin Labs
 */
@Slf4j
@CompileStatic
final class LaminPath implements Path {

    private final LaminFileSystem fileSystem
    private final LaminUriParser parsed
    private final boolean isFileName  // When true, toString() returns just the filename

    /**
     * Create a new LaminPath.
     *
     * @param fileSystem The LaminFileSystem this path belongs to
     * @param parsed The parsed URI components
     */
    LaminPath(LaminFileSystem fileSystem, LaminUriParser parsed) {
        this(fileSystem, parsed, false)
    }

    /**
     * Create a new LaminPath.
     *
     * @param fileSystem The LaminFileSystem this path belongs to
     * @param parsed The parsed URI components
     * @param isFileName When true, toString() returns just the filename component
     */
    LaminPath(LaminFileSystem fileSystem, LaminUriParser parsed, boolean isFileName) {
        if (fileSystem == null) {
            throw new IllegalArgumentException("FileSystem cannot be null")
        }
        if (parsed == null) {
            throw new IllegalArgumentException("Parsed URI cannot be null")
        }
        this.fileSystem = fileSystem
        this.parsed = parsed
        this.isFileName = isFileName
    }

    /**
     * Get the parsed URI components.
     */
    LaminUriParser getParsed() {
        return parsed
    }

    /**
     * Get the owner of the LaminDB instance.
     */
    String getOwner() {
        return parsed.owner
    }

    /**
     * Get the name of the LaminDB instance.
     */
    String getInstance() {
        return parsed.instance
    }

    /**
     * Get the resource type (e.g., "artifact").
     */
    String getResourceType() {
        return parsed.resourceType
    }

    /**
     * Get the resource ID (e.g., artifact UID).
     */
    String getResourceId() {
        return parsed.resourceId
    }

    /**
     * Get the sub-path within the artifact (if any).
     */
    String getSubPath() {
        return parsed.subPath
    }

    /**
     * Convert this path to a URI string.
     */
    String toUriString() {
        return parsed.toUriString()
    }

    // ==================== Path Interface Implementation ====================

    @Override
    FileSystem getFileSystem() {
        return fileSystem
    }

    @Override
    boolean isAbsolute() {
        // Lamin paths are always absolute (they have a scheme)
        return true
    }

    @Override
    Path getRoot() {
        if (!isAbsolute()) {
            return null
        }
        // Root is the artifact without sub-path
        LaminUriParser rootParsed = parsed.withoutSubPath()
        return new LaminPath(fileSystem, rootParsed)
    }

    @Override
    Path getFileName() {
        String fileName = parsed.fileName
        if (fileName == null || fileName.isEmpty()) {
            return null
        }
        // Return a path marked as isFileName=true so toString() returns just the filename
        return new LaminPath(fileSystem, parsed, true)
    }

    @Override
    Path getParent() {
        LaminUriParser parentParsed = parsed.parent
        if (parentParsed == null) {
            return null
        }
        return new LaminPath(fileSystem, parentParsed)
    }

    @Override
    int getNameCount() {
        // Count: owner, instance, resourceType, resourceId, plus sub-path components
        int count = 4  // owner/instance/resourceType/resourceId
        if (parsed.hasSubPath()) {
            count += parsed.subPath.split(LaminUriParser.SEP).length
        }
        return count
    }

    @Override
    Path getName(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Index cannot be negative: ${index}")
        }

        List<String> components = []
        components.add(parsed.owner)
        components.add(parsed.instance)
        components.add(parsed.resourceType)
        components.add(parsed.resourceId)
        if (parsed.hasSubPath()) {
            components.addAll(parsed.subPath.split(LaminUriParser.SEP))
        }

        if (index >= components.size()) {
            throw new IllegalArgumentException("Index ${index} out of bounds for path with ${components.size()} components")
        }

        // Return a path representing just that name component
        // This is a simplified implementation
        return this
    }

    @Override
    Path subpath(int beginIndex, int endIndex) {
        if (beginIndex < 0 || endIndex < 0 || beginIndex >= endIndex) {
            throw new IllegalArgumentException("Invalid subpath indices: begin=${beginIndex}, end=${endIndex}")
        }
        // Simplified implementation
        return this
    }

    @Override
    boolean startsWith(Path other) {
        if (!(other instanceof LaminPath)) {
            return false
        }
        return toUriString().startsWith(((LaminPath) other).toUriString())
    }

    @Override
    boolean startsWith(String other) {
        return toUriString().startsWith(other)
    }

    @Override
    boolean endsWith(Path other) {
        if (!(other instanceof LaminPath)) {
            return false
        }
        return toUriString().endsWith(((LaminPath) other).toUriString())
    }

    @Override
    boolean endsWith(String other) {
        return toUriString().endsWith(other)
    }

    @Override
    Path normalize() {
        // Lamin paths are already normalized
        return this
    }

    @Override
    Path resolve(Path other) {
        if (other == null) {
            return this
        }

        if (other.isAbsolute()) {
            return other
        }

        // Resolve the other path relative to this path
        String otherStr = other.toString()
        LaminUriParser newParsed = parsed.withSubPath(otherStr)
        return new LaminPath(fileSystem, newParsed)
    }

    @Override
    Path resolve(String other) {
        if (other == null || other.isEmpty()) {
            return this
        }

        // If it's an absolute path (has scheme), parse it
        if (other.startsWith(LaminUriParser.SCHEME + ':')) {
            return new LaminPath(fileSystem, LaminUriParser.parse(other))
        }

        // Otherwise resolve relative to this path
        LaminUriParser newParsed = parsed.withSubPath(other)
        return new LaminPath(fileSystem, newParsed)
    }

    @Override
    Path resolveSibling(Path other) {
        if (other == null) {
            return getParent()
        }
        Path parent = getParent()
        if (parent == null) {
            return other
        }
        return parent.resolve(other)
    }

    @Override
    Path resolveSibling(String other) {
        return resolveSibling(fileSystem.getPath(other))
    }

    @Override
    Path relativize(Path other) {
        if (!(other instanceof LaminPath)) {
            throw new IllegalArgumentException("Cannot relativize against non-LaminPath: ${other.class}")
        }

        LaminPath otherPath = (LaminPath) other
        String thisUri = toUriString()
        String otherUri = otherPath.toUriString()

        if (!otherUri.startsWith(thisUri)) {
            throw new IllegalArgumentException("Cannot relativize '${otherUri}' against '${thisUri}'")
        }

        String relative = otherUri.substring(thisUri.length())
        if (relative.startsWith('/')) {
            relative = relative.substring(1)
        }

        // Return a relative path (simplified)
        return fileSystem.getPath(relative)
    }

    @Override
    URI toUri() {
        return parsed.toUri()
    }

    @Override
    Path toAbsolutePath() {
        // Already absolute
        return this
    }

    @Override
    Path toRealPath(LinkOption... options) throws IOException {
        // Return self - the "real" path is resolved at file operation time
        return this
    }

    @Override
    File toFile() {
        throw new UnsupportedOperationException("LaminPath cannot be converted to File. Use toUri() instead.")
    }

    @Override
    WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException("Watch service is not supported for LaminPath")
    }

    @Override
    WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
        throw new UnsupportedOperationException("Watch service is not supported for LaminPath")
    }

    @Override
    Iterator<Path> iterator() {
        // Return iterator over path components
        List<Path> paths = []
        // Simplified: just return this path
        paths.add(this)
        return paths.iterator()
    }

    @Override
    int compareTo(Path other) {
        if (!(other instanceof LaminPath)) {
            return -1
        }
        return toUriString().compareTo(((LaminPath) other).toUriString())
    }

    @Override
    String toString() {
        // When isFileName is true (i.e., this path was returned by getFileName()),
        // return just the filename component so it can be used for staging paths
        if (isFileName) {
            return parsed.fileName
        }
        return toUriString()
    }

    @Override
    boolean equals(Object obj) {
        if (this.is(obj)) return true
        if (!(obj instanceof LaminPath)) return false
        LaminPath other = (LaminPath) obj
        return parsed == other.parsed
    }

    @Override
    int hashCode() {
        return parsed.hashCode()
    }
}
