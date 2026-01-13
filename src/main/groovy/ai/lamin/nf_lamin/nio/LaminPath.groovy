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

    /**
     * Resolve this lamin:// path to its underlying storage path.
     *
     * This method queries the LaminDB API to find the artifact's actual storage
     * location (S3, GCS, local filesystem, etc.) and returns a Path object
     * pointing to that location.
     *
     * Example:
     * <pre>
     * def laminPath = file('lamin://laminlabs/lamindata/artifact/uid123')
     * def s3Path = laminPath.resolveToStorage()
     * // s3Path might be: s3://bucket/.lamindb/uid123.h5ad
     * </pre>
     *
     * Note: This requires that the LaminRunManager has been initialized with
     * an API key (i.e., the workflow has started with lamin plugin configured).
     *
     * @return A Path to the underlying storage (e.g., S3Path, GcsPath, local Path)
     * @throws IllegalStateException if LaminRunManager is not initialized
     */
    Path resolveToStorage() {
        LaminFileSystemProvider provider = (LaminFileSystemProvider) fileSystem.provider()
        return provider.resolveToUnderlyingPath(this)
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
        // Lamin URIs (lamin://owner/instance/artifact/uid) are not hierarchical file paths.
        // Individual components cannot exist as standalone paths - you cannot navigate to
        // 'lamin://owner/' or 'lamin://owner/instance/artifact/' without a complete artifact UID.
        // If you're seeing this error, you may be using a lamin:// path in a context that
        // expects a traditional filesystem path.
        throw new UnsupportedOperationException(
            "getName() is not supported for lamin:// URIs. Lamin paths reference artifacts by UID " +
            "(e.g., lamin://owner/instance/artifact/uid) and individual path components are not " +
            "valid standalone paths. Path: ${toUriString()}"
        )
    }

    @Override
    Path subpath(int beginIndex, int endIndex) {
        // Lamin URIs (lamin://owner/instance/artifact/uid) are not hierarchical file paths.
        // You cannot extract partial paths like 'owner/instance' as they are not valid lamin URIs.
        // If you're seeing this error, you may be using a lamin:// path in a context that
        // expects a traditional filesystem path.
        throw new UnsupportedOperationException(
            "subpath() is not supported for lamin:// URIs. Lamin paths reference artifacts by UID " +
            "(e.g., lamin://owner/instance/artifact/uid) and partial paths are not valid. " +
            "Path: ${toUriString()}"
        )
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
        if (other == null || other.isEmpty()) {
            return getParent()
        }

        // If it's an absolute lamin:// URI, parse and return it directly
        if (other.startsWith(LaminUriParser.SCHEME + ':')) {
            return new LaminPath(fileSystem, LaminUriParser.parse(other))
        }

        // Otherwise, resolve relative to parent
        Path parent = getParent()
        if (parent == null) {
            // No parent, treat as relative path from artifact root
            LaminUriParser newParsed = parsed.withoutSubPath().withSubPath(other)
            return new LaminPath(fileSystem, newParsed)
        }
        return parent.resolve(other)
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

        // Return a relative path using the default filesystem
        // This is appropriate since the result is a relative path, not a lamin:// URI
        if (relative.isEmpty()) {
            return java.nio.file.Paths.get('')
        }
        return java.nio.file.Paths.get(relative)
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
        // Lamin URIs (lamin://owner/instance/artifact/uid) are not hierarchical file paths.
        // Individual components cannot be iterated as Path objects since they are not valid
        // standalone lamin URIs. If you're seeing this error, you may be using a lamin:// path
        // in a context that expects a traditional filesystem path.
        throw new UnsupportedOperationException(
            "iterator() is not supported for lamin:// URIs. Lamin paths reference artifacts by UID " +
            "(e.g., lamin://owner/instance/artifact/uid) and individual path components cannot be " +
            "iterated as standalone paths. Path: ${toUriString()}"
        )
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
