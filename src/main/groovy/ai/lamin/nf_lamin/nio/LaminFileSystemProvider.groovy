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

import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystemNotFoundException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.ProviderMismatchException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider

import nextflow.file.FileHelper
import nextflow.file.FileSystemTransferAware
import nextflow.file.CopyOptions

import ai.lamin.nf_lamin.LaminRunManager
import ai.lamin.nf_lamin.instance.Instance

/**
 * FileSystemProvider implementation for Lamin URIs.
 *
 * This provider handles lamin:// URIs, resolving them to their underlying
 * storage paths (S3, GCS, local) and delegating file operations to the
 * appropriate underlying provider.
 *
 * This provider reuses the LaminHub and Instance clients from LaminRunManager
 * to avoid creating duplicate connections and to share configuration.
 *
 * @author Lamin Labs
 */
@Slf4j
@CompileStatic
class LaminFileSystemProvider extends FileSystemProvider implements FileSystemTransferAware {

    static final String SCHEME = LaminUriParser.SCHEME

    // Cache of file systems by instance slug
    private final Map<String, LaminFileSystem> fileSystems = Collections.synchronizedMap(new LinkedHashMap<String, LaminFileSystem>())

    /**
     * Get an Instance client for a specific LaminDB instance.
     * Delegates to LaminRunManager which manages the hub and instance cache.
     *
     * @param owner The instance owner
     * @param name The instance name
     * @return The Instance client
     * @throws IllegalStateException if LaminRunManager is not initialized
     */
    Instance getInstance(String owner, String name) {
        LaminRunManager manager = LaminRunManager.getInstance()

        // Check if the manager has been initialized with a hub
        if (manager.getHub() == null) {
            throw new IllegalStateException(
                "LaminRunManager not initialized. Ensure the lamin plugin is configured with an API key " +
                "and the workflow has started before using lamin:// URIs."
            )
        }

        return manager.getInstance(owner, name)
    }

    /**
     * Remove a file system from the cache.
     */
    void removeFileSystem(String instanceSlug) {
        fileSystems.remove(instanceSlug)
    }

    /**
     * Cast a Path to LaminPath, throwing if it's not a LaminPath.
     */
    static LaminPath toLaminPath(Path path) {
        if (path instanceof LaminPath) {
            return (LaminPath) path
        }
        throw new ProviderMismatchException("Not a LaminPath: ${path?.class?.name}")
    }

    // ==================== FileSystemProvider Core Methods ====================

    @Override
    String getScheme() {
        return SCHEME
    }

    @Override
    FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        log.debug "newFileSystem: uri=${uri}, env=${env}"

        LaminUriParser parsed = LaminUriParser.parse(uri)
        String instanceSlug = parsed.instanceSlug

        synchronized (fileSystems) {
            if (fileSystems.containsKey(instanceSlug)) {
                return fileSystems.get(instanceSlug)
            }

            LaminFileSystem fs = new LaminFileSystem(this, instanceSlug)
            fileSystems.put(instanceSlug, fs)
            return fs
        }
    }

    @Override
    FileSystem getFileSystem(URI uri) {
        LaminUriParser parsed = LaminUriParser.parse(uri)
        String instanceSlug = parsed.instanceSlug

        LaminFileSystem fs = fileSystems.get(instanceSlug)
        if (fs == null) {
            throw new FileSystemNotFoundException("No file system for: ${instanceSlug}")
        }
        return fs
    }

    /**
     * Get or create a file system for the given URI.
     */
    LaminFileSystem getOrCreateFileSystem(URI uri) {
        LaminUriParser parsed = LaminUriParser.parse(uri)
        String instanceSlug = parsed.instanceSlug

        synchronized (fileSystems) {
            LaminFileSystem fs = fileSystems.get(instanceSlug)
            if (fs == null) {
                fs = new LaminFileSystem(this, instanceSlug)
                fileSystems.put(instanceSlug, fs)
            }
            return fs
        }
    }

    @Override
    Path getPath(URI uri) {
        log.debug "getPath: uri=${uri}"

        LaminUriParser parsed = LaminUriParser.parse(uri)
        LaminFileSystem fs = getOrCreateFileSystem(uri)
        return new LaminPath(fs, parsed)
    }

    // ==================== Path Resolution ====================

    /**
     * Resolve a LaminPath to its underlying storage path.
     *
     * This method queries the LaminDB API to find the artifact's storage
     * location and returns a Path object pointing to that location.
     *
     * Note: This currently relies on credentials being configured in nextflow.config
     * (e.g., aws.accessKey/secretKey). Future versions will support automatic
     * credential federation from Lamin Hub.
     *
     * @param laminPath The LaminPath to resolve
     * @return A Path to the underlying storage (e.g., S3Path, GcsPath, local Path)
     */
    Path resolveToUnderlyingPath(LaminPath laminPath) {
        log.debug "Resolving LaminPath to underlying storage: ${laminPath}"

        LaminFileSystem fs = (LaminFileSystem) laminPath.fileSystem
        Instance instance = fs.laminInstance

        // Get the artifact storage info
        String uid = laminPath.resourceId
        Map<String, Object> artifactInfo = instance.getArtifactStorageInfo(uid)

        String storageRoot = artifactInfo.storageRoot as String
        String artifactKey = artifactInfo.artifactKey as String

        // Build the full path
        String fullPath = storageRoot.endsWith('/') ? storageRoot + artifactKey : storageRoot + '/' + artifactKey

        // Use FileHelper to create the path - this will use credentials from nextflow.config
        Path artifactPath = FileHelper.asPath(fullPath)

        // If there's a sub-path, resolve it
        if (laminPath.subPath) {
            artifactPath = artifactPath.resolve(laminPath.subPath)
        }

        log.debug "Resolved ${laminPath.toUri()} to ${artifactPath.toUri()}"
        return artifactPath
    }

    // ==================== File Operations (Delegated) ====================

    @Override
    InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        LaminPath laminPath = toLaminPath(path)
        Path underlying = resolveToUnderlyingPath(laminPath)
        return Files.newInputStream(underlying, options)
    }

    @Override
    OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        throw new UnsupportedOperationException("Writing to lamin:// paths is not supported")
    }

    @Override
    SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        LaminPath laminPath = toLaminPath(path)
        Path underlying = resolveToUnderlyingPath(laminPath)
        return Files.newByteChannel(underlying, options, attrs)
    }

    @Override
    DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        LaminPath laminPath = toLaminPath(dir)
        Path underlying = resolveToUnderlyingPath(laminPath)

        // Get the underlying directory stream
        DirectoryStream<Path> underlyingStream = Files.newDirectoryStream(underlying, filter)

        // Wrap paths back to LaminPath (simplified - just return underlying for now)
        return underlyingStream
    }

    @Override
    void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException("Creating directories in lamin:// paths is not supported")
    }

    @Override
    void delete(Path path) throws IOException {
        throw new UnsupportedOperationException("Deleting lamin:// paths is not supported")
    }

    @Override
    void copy(Path source, Path target, CopyOption... options) throws IOException {
        // Handle copy from lamin:// to another path
        if (source instanceof LaminPath) {
            LaminPath laminSource = (LaminPath) source
            Path underlyingSource = resolveToUnderlyingPath(laminSource)
            Files.copy(underlyingSource, target, options)
            return
        }

        throw new UnsupportedOperationException("Copying to lamin:// paths is not supported")
    }

    @Override
    void move(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException("Moving lamin:// paths is not supported")
    }

    @Override
    boolean isSameFile(Path path1, Path path2) throws IOException {
        if (!(path1 instanceof LaminPath) || !(path2 instanceof LaminPath)) {
            return false
        }
        return path1.equals(path2)
    }

    @Override
    boolean isHidden(Path path) throws IOException {
        return false
    }

    @Override
    FileStore getFileStore(Path path) throws IOException {
        throw new UnsupportedOperationException("FileStore is not supported for lamin:// paths")
    }

    @Override
    void checkAccess(Path path, AccessMode... modes) throws IOException {
        LaminPath laminPath = toLaminPath(path)
        Path underlying = resolveToUnderlyingPath(laminPath)
        underlying.fileSystem.provider().checkAccess(underlying, modes)
    }

    @Override
    <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        try {
            LaminPath laminPath = toLaminPath(path)
            Path underlying = resolveToUnderlyingPath(laminPath)
            return Files.getFileAttributeView(underlying, type, options)
        } catch (IOException e) {
            log.error "Error getting file attribute view for ${path}", e
            return null
        }
    }

    @Override
    <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        LaminPath laminPath = toLaminPath(path)
        Path underlying = resolveToUnderlyingPath(laminPath)
        return Files.readAttributes(underlying, type, options)
    }

    @Override
    Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        LaminPath laminPath = toLaminPath(path)
        Path underlying = resolveToUnderlyingPath(laminPath)
        return Files.readAttributes(underlying, attributes, options)
    }

    @Override
    void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Setting attributes on lamin:// paths is not supported")
    }

    // ==================== FileSystemTransferAware Interface ====================

    @Override
    boolean canUpload(Path source, Path target) {
        // Cannot upload TO lamin:// paths
        return false
    }

    @Override
    boolean canDownload(Path source, Path target) {
        // Can download FROM lamin:// paths to local paths
        return source instanceof LaminPath && isLocalFileSystem(target)
    }

    @Override
    void download(Path remoteFile, Path localDestination, CopyOption... options) throws IOException {
        log.debug "download: ${remoteFile} -> ${localDestination}"

        LaminPath laminPath = toLaminPath(remoteFile)
        Path underlying = resolveToUnderlyingPath(laminPath)

        CopyOptions opts = CopyOptions.parse(options)
        if (opts.replaceExisting()) {
            FileHelper.deletePath(localDestination)
        }

        // Delegate to underlying provider if it supports transfer
        if (underlying.fileSystem.provider() instanceof FileSystemTransferAware) {
            FileSystemTransferAware transferAware = (FileSystemTransferAware) underlying.fileSystem.provider()
            if (transferAware.canDownload(underlying, localDestination)) {
                transferAware.download(underlying, localDestination, options)
                return
            }
        }

        // Fall back to standard copy
        if (Files.isDirectory(underlying)) {
            copyDirectory(underlying, localDestination, options)
        } else {
            Files.copy(underlying, localDestination, options)
        }
    }

    @Override
    void upload(Path localFile, Path remoteDestination, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException("Uploading to lamin:// paths is not supported")
    }

    // ==================== Helper Methods ====================

    private static boolean isLocalFileSystem(Path path) {
        return path.fileSystem == java.nio.file.FileSystems.getDefault()
    }

    private static void copyDirectory(Path source, Path target, CopyOption... options) throws IOException {
        Files.createDirectories(target)
        Files.walk(source).forEach { Path sourcePath ->
            Path targetPath = target.resolve(source.relativize(sourcePath))
            if (Files.isDirectory(sourcePath)) {
                Files.createDirectories(targetPath)
            } else {
                Files.copy(sourcePath, targetPath, options)
            }
        }
    }
}
