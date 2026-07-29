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
 */
@Slf4j
@CompileStatic
final class LaminPath implements Path {

    private final LaminFileSystem fileSystem
    private final LaminUriParser parsed
    /** Relative path this object represents, or null when the path is absolute. */
    private final String relativePath

    /**
     * Create a new absolute LaminPath.
     *
     * @param fileSystem The LaminFileSystem this path belongs to
     * @param parsed The parsed URI components
     */
    LaminPath(LaminFileSystem fileSystem, LaminUriParser parsed) {
        this(fileSystem, parsed, (String) null)
    }

    /**
     * Create a new LaminPath.
     *
     * @param fileSystem The LaminFileSystem this path belongs to
     * @param parsed The parsed URI components
     * @param relativePath When set, this path is a relative path (as returned by
     *        {@link #getFileName()} or {@link #relativize}) rather than an absolute path
     */
    LaminPath(LaminFileSystem fileSystem, LaminUriParser parsed, String relativePath) {
        if (fileSystem == null) {
            throw new IllegalArgumentException("FileSystem cannot be null")
        }
        if (parsed == null) {
            throw new IllegalArgumentException("Parsed URI cannot be null")
        }
        this.fileSystem = fileSystem
        this.parsed = parsed
        this.relativePath = relativePath
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
        // A name element (as returned by getFileName()) is relative; anything else carries
        // a full lamin:// URI and is therefore absolute
        return relativePath == null
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
        String fileName = isAbsolute() ? parsed.fileName : lastSegment(relativePath)
        if (fileName == null || fileName.isEmpty()) {
            return null
        }
        return new LaminPath(fileSystem, parsed, fileName)
    }

    /** The segments of this path that behave hierarchically, i.e. below the root. */
    private List<String> nameSegments() {
        if (!isAbsolute()) {
            return splitSegments(relativePath)
        }
        return parsed.storage ? parsed.keySegments : null
    }

    private static List<String> splitSegments(String value) {
        return value ? (value.split(LaminUriParser.SEP) as List<String>) : ([] as List<String>)
    }

    private static String lastSegment(String value) {
        if (!value) {
            return null
        }
        int lastSep = value.lastIndexOf(LaminUriParser.SEP)
        return lastSep >= 0 ? value.substring(lastSep + 1) : value
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
        List<String> segments = nameSegments()
        if (segments != null) {
            return segments.size()
        }
        // Count: owner, instance, resourceType, resourceId, plus sub-path components
        int count = 4  // owner/instance/resourceType/resourceId
        if (parsed.hasSubPath()) {
            count += parsed.subPath.split(LaminUriParser.SEP).length
        }
        return count
    }

    @Override
    Path getName(int index) {
        List<String> segments = nameSegments()
        if (segments != null) {
            if (index < 0 || index >= segments.size()) {
                throw new IllegalArgumentException("Index ${index} out of range [0, ${segments.size()})")
            }
            return new LaminPath(fileSystem, parsed, segments[index])
        }
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
        List<String> segments = nameSegments()
        if (segments != null) {
            if (beginIndex < 0 || endIndex > segments.size() || beginIndex >= endIndex) {
                throw new IllegalArgumentException("Invalid subpath range [${beginIndex}, ${endIndex})")
            }
            return new LaminPath(fileSystem, parsed, segments[beginIndex..<endIndex].join(LaminUriParser.SEP))
        }
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
        LaminPath that = (LaminPath) other
        if (isAbsolute() != that.isAbsolute()) {
            return false
        }
        if (!isAbsolute()) {
            return segmentsStartWith(relativePath, that.relativePath)
        }
        // For a storage location, compare keys segment by segment so that 'results' is not
        // reported as a prefix of 'results-old'
        if (parsed.storage && that.parsed.storage) {
            return sameStorageLocation(that) && segmentsStartWith(parsed.key, that.parsed.key)
        }
        return toString().startsWith(other.toString())
    }

    /** Whether both paths address the same storage location, ignoring their keys. */
    private boolean sameStorageLocation(LaminPath that) {
        return parsed.owner == that.parsed.owner &&
               parsed.instance == that.parsed.instance &&
               parsed.resourceType == that.parsed.resourceType &&
               parsed.resourceId == that.parsed.resourceId &&
               parsed.storageRef == that.parsed.storageRef
    }

    private static boolean segmentsStartWith(String value, String prefix) {
        if (!prefix) {
            return true
        }
        if (!value) {
            return false
        }
        return value == prefix || value.startsWith(prefix + LaminUriParser.SEP)
    }

    @Override
    boolean startsWith(String other) {
        return toString().startsWith(other)
    }

    @Override
    boolean endsWith(Path other) {
        if (!(other instanceof LaminPath)) {
            return false
        }
        return toString().endsWith(other.toString())
    }

    @Override
    boolean endsWith(String other) {
        return toString().endsWith(other)
    }

    @Override
    Path normalize() {
        if (!isAbsolute()) {
            String normalized = relativePath ? (LaminUriParser.normalizeKey(relativePath) ?: '') : relativePath
            return normalized == relativePath ? this : new LaminPath(fileSystem, parsed, normalized)
        }
        // Keys are normalised as they are built, and artifact URIs have nothing to normalise
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

        return resolve(other.toString())
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

        if (!isAbsolute()) {
            String joined = relativePath ? "${relativePath}/${other}".toString() : other
            return new LaminPath(fileSystem, parsed, joined)
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

        // For a storage location the result stays on this provider, so that walking it with
        // Files.createDirectories/walkFileTree keeps producing LaminPaths
        if (isAbsolute() && parsed.storage && otherPath.isAbsolute() && otherPath.parsed.storage) {
            if (!otherPath.startsWith(this)) {
                throw new IllegalArgumentException("Cannot relativize '${otherPath}' against '${this}'")
            }
            String relativeKey = parsed.hasKey()
                ? otherPath.parsed.key.substring(parsed.key.length()).replaceFirst('^/', '')
                : otherPath.parsed.key
            return new LaminPath(fileSystem, parsed, relativeKey ?: '')
        }

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
        if (!isAbsolute()) {
            throw new IllegalStateException("Cannot build a URI for the relative path '${relativePath}'")
        }
        return parsed.toUri()
    }

    @Override
    Path toAbsolutePath() {
        // Already absolute
        return this
    }

    /**
     * Resolve this lamin:// path to its underlying storage path, like a symlink
     * resolving to its target (see {@link #resolveToStorage()}). With
     * {@link LinkOption#NOFOLLOW_LINKS} the path is returned as-is.
     *
     * @throws IOException if the artifact cannot be resolved
     */
    @Override
    Path toRealPath(LinkOption... options) throws IOException {
        if (options.contains(LinkOption.NOFOLLOW_LINKS)) {
            return this
        }
        try {
            return resolveToStorage()
        }
        catch (IOException e) {
            throw e
        }
        catch (Exception e) {
            throw new IOException("Cannot resolve ${toUriString()} to its underlying storage path", e)
        }
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
        List<String> segments = nameSegments()
        if (segments != null) {
            return segments.collect { String s -> (Path) new LaminPath(fileSystem, parsed, s) }.iterator()
        }
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
        return toString().compareTo(other.toString())
    }

    @Override
    String toString() {
        // A relative path renders as just that path, so it can be used for staging paths
        return isAbsolute() ? toUriString() : relativePath
    }

    @Override
    boolean equals(Object obj) {
        if (this.is(obj)) return true
        if (!(obj instanceof LaminPath)) return false
        LaminPath other = (LaminPath) obj
        return parsed == other.parsed && relativePath == other.relativePath
    }

    @Override
    int hashCode() {
        return Objects.hash(parsed, relativePath)
    }
}
