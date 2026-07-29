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
import java.nio.file.AccessDeniedException
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
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Supplier

import nextflow.file.FileHelper
import nextflow.file.FileSystemTransferAware
import nextflow.file.CopyOptions

import ai.lamin.nf_lamin.LaminConfig
import ai.lamin.nf_lamin.LaminConnection
import ai.lamin.nf_lamin.hub.CloudAccessResponse
import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.instance.Instance

/**
 * FileSystemProvider implementation for Lamin URIs.
 *
 * This provider handles lamin:// URIs, resolving them to their underlying
 * storage paths (S3, GCS, local) and delegating file operations to the
 * appropriate underlying provider.
 *
 * This provider obtains LaminHub and Instance clients from {@link LaminConnection},
 * which shares the authenticated connection with run tracking when a run is active and
 * otherwise builds an anonymous-capable connection so that lamin:// URIs can be resolved
 * with nothing more than {@code -plugins nf-lamin}.
 */
@Slf4j
@CompileStatic
class LaminFileSystemProvider extends FileSystemProvider implements FileSystemTransferAware {

    static final String SCHEME = LaminUriParser.SCHEME

    // Cache of file systems by instance slug
    private final Map<String, LaminFileSystem> fileSystems = Collections.synchronizedMap(new LinkedHashMap<String, LaminFileSystem>())

    // Cache: storageRoot -> CloudAccessResponse
    // Avoids calling getCloudAccess() on every artifact resolution.
    private final Map<String, CloudAccessResponse> cloudAccessCache = Collections.synchronizedMap(new LinkedHashMap<String, CloudAccessResponse>())
    private final Map<String, ReentrantLock> cloudAccessLocks = new ConcurrentHashMap<String, ReentrantLock>()

    // Resolves (and caches) the storage location behind a publish target
    private final LaminStorageResolver storageResolver = new LaminStorageResolver()

    /**
     * Get an Instance client for a specific LaminDB instance.
     * Delegates to {@link LaminConnection}, which shares the hub and instance cache with
     * run tracking when active and otherwise builds an anonymous-capable connection.
     *
     * @param owner The instance owner
     * @param name The instance name
     * @return The Instance client
     */
    Instance getInstance(String owner, String name) {
        return LaminConnection.getInstance().getInstance(owner, name)
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
        return getPath(LaminUriParser.parse(uri))
    }

    /**
     * Build a path for an already-parsed URI, creating the file system if needed.
     */
    LaminPath getPath(LaminUriParser parsed) {
        LaminFileSystem fs
        String instanceSlug = parsed.instanceSlug
        synchronized (fileSystems) {
            fs = fileSystems.get(instanceSlug)
            if (fs == null) {
                fs = new LaminFileSystem(this, instanceSlug)
                fileSystems.put(instanceSlug, fs)
            }
        }
        return new LaminPath(fs, parsed)
    }

    // ==================== Path Resolution ====================

    /**
     * Resolve a LaminPath to its underlying storage path.
     *
     * This method queries the LaminDB API to find the artifact's storage location and
     * returns a Path object pointing to that location.
     *
     * - For S3-backed artifacts in Lamin-managed buckets, this method attempts to obtain
     *   temporary session credentials.
     * - If the storage is not managed by LaminHub (public or externally-managed bucket) the
     *   method falls back to resolving through the standard nf-amazon {@code s3://} provider.
     *
     * @param laminPath The LaminPath to resolve
     * @return A Path to the underlying storage (LaminS3Path, S3Path, GcsPath, local Path, ...)
     */
    Path resolveToUnderlyingPath(LaminPath laminPath) {
        return resolveToUnderlyingPath(laminPath, false)
    }

    /**
     * Resolve a LaminPath to its underlying storage path.
     *
     * @param laminPath The LaminPath to resolve
     * @param forWrite Whether the caller intends to write to the resolved path
     * @return A Path to the underlying storage (LaminS3Path, S3Path, GcsPath, local Path, ...)
     */
    Path resolveToUnderlyingPath(LaminPath laminPath, boolean forWrite) {
        log.debug "Resolving LaminPath to underlying storage: ${laminPath}"

        LaminFileSystem fs = (LaminFileSystem) laminPath.fileSystem
        Instance instance = fs.laminInstance

        if (laminPath.parsed.storage) {
            return resolveStoragePath(laminPath, instance, forWrite)
        }

        if (forWrite) {
            throw new AccessDeniedException(
                laminPath.toUriString(), null,
                "artifact URIs are read-only; publish to 'lamin://${laminPath.owner}/${laminPath.instance}' instead"
            )
        }

        // Retrieve storage root (e.g. "s3://lamin-us-east-1/JwMEKs04D9WJ") and relative key
        String uid = laminPath.resourceId
        Map<String, Object> artifactInfo = instance.getArtifactStorageInfo(uid)

        String storageRoot = artifactInfo.storageRoot as String
        String artifactKey = artifactInfo.artifactKey as String

        // Attempt credential federation for Lamin-managed S3 storage
        if (storageRoot?.startsWith('s3://')) {
            if (isCredentialManagementEnabled()) {
                Path managed = tryResolveWithManagedS3Credentials(laminPath, storageRoot, artifactKey, false, null)
                if (managed != null) {
                    return managed
                }
            }
        }

        // Fall back: resolve via the standard nf-amazon s3:// (or gs://, local, …) provider
        String fullPath = storageRoot.endsWith('/') ? storageRoot + artifactKey : storageRoot + '/' + artifactKey
        Path artifactPath = FileHelper.asPath(fullPath)

        if (laminPath.subPath) {
            artifactPath = artifactPath.resolve(laminPath.subPath)
        }

        log.debug "Resolved ${laminPath.toUri()} to ${artifactPath.toUri()} (standard provider)"
        return artifactPath
    }

    /**
     * Resolve a publish target to the location in the instance's storage that it names.
     */
    private Path resolveStoragePath(LaminPath laminPath, Instance instance, boolean forWrite) {
        LaminStorageTarget target = storageResolver.resolve(instance, laminPath.parsed)
        String storageRoot = target.storageRoot
        String key = laminPath.parsed.key

        if (storageRoot?.startsWith('s3://') && isCredentialManagementEnabled()) {
            Path managed = tryResolveWithManagedS3Credentials(laminPath, storageRoot, key, forWrite, target.region)
            if (managed != null) {
                return managed
            }
        }

        Path path = FileHelper.asPath(target.resolveUri(key))
        log.debug "Resolved ${laminPath} to ${path.toUri()} (standard provider)"
        return path
    }

    /**
     * The storage location behind a publish target, for callers that need its space or UID
     * rather than a path.
     */
    LaminStorageTarget resolveStorageTarget(LaminPath laminPath) {
        LaminFileSystem fs = (LaminFileSystem) laminPath.fileSystem
        return storageResolver.resolve(fs.laminInstance, laminPath.parsed)
    }

    private static boolean isCredentialManagementEnabled() {
        LaminConfig config = LaminConnection.getInstance().getConfig()
        return config?.features?.manage_s3_credentials != false
    }

    /**
     * Return cloud access credentials for the given storage root, using a per-root cache.
     *
     * Cached entries are re-used until 5 minutes before their STS expiry (or 55 minutes
     * if no Expiration field is present). A stale entry triggers a fresh call to
     * {@code LaminHub.getCloudAccess()}.
     */
    private CloudAccessResponse getCachedCloudAccess(LaminHub hub, String storageRoot) {
        CloudAccessResponse cached = cloudAccessCache.get(storageRoot)
        if (cached != null && !cached.isCacheExpired()) {
            log.debug "Using cached cloud credentials for ${storageRoot}"
            return cached
        }

        // The AWS SDK asks for credentials on every request, so without a lock a burst of
        // concurrent transfers would each refresh them
        ReentrantLock lock = cloudAccessLocks.computeIfAbsent(storageRoot) { new ReentrantLock() }
        lock.lock()
        try {
            cached = cloudAccessCache.get(storageRoot)
            if (cached != null && !cached.isCacheExpired()) {
                return cached
            }
            if (cached != null) {
                log.debug "Cached cloud credentials for ${storageRoot} expired, re-fetching"
            }

            CloudAccessResponse fresh = hub.getCloudAccess(storageRoot)
            if (fresh.hasCredentials()) {
                cloudAccessCache.put(storageRoot, fresh)
            }
            return fresh
        }
        finally {
            lock.unlock()
        }
    }

    /**
     * Attempt to resolve a LaminPath to a {@link LaminS3Path} using Lamin-managed STS credentials.
     *
     * Credentials are obtained from LaminHub and are scoped to the given {@code storageRoot}.
     * Multiple storage roots may share the same S3 bucket but carry independent credentials.
     *
     * @param laminPath   The LaminPath being resolved
     * @param storageRoot The full storage root URI (e.g. {@code s3://bucket/prefix})
     * @param artifactKey The relative key of the artifact within the storage root
     * @param forWrite    Whether the caller intends to write to the resolved path
     * @param region      Storage region, or null to let cross-region access sort it out
     * @return A {@link LaminS3Path} if managed credentials are available, or {@code null} to fall back
     * @throws AccessDeniedException if write access was asked for but LaminHub granted only read
     */
    private Path tryResolveWithManagedS3Credentials(LaminPath laminPath, String storageRoot, String artifactKey,
                                                    boolean forWrite, String region) {
        LaminHub hub
        CloudAccessResponse cloudAccess
        try {
            hub = LaminConnection.getInstance().getHub()
            if (hub == null) {
                return null
            }

            cloudAccess = getCachedCloudAccess(hub, storageRoot)
            if (!cloudAccess.isUsable()) {
                log.debug "No Lamin-managed credentials for ${storageRoot} (public or unsupported storage) — falling back to standard provider"
                return null
            }
        } catch (Exception e) {
            log.warn "Could not obtain cloud credentials for ${storageRoot}, falling back to standard S3 path: ${e.message}"
            log.debug "getCloudAccess failure detail", e
            return null
        }

        // Once LaminHub has spoken, honour what it said: falling back to the ambient AWS
        // credentials here would write under a different identity than the one authorised
        if (forWrite && cloudAccess.role != 'write') {
            throw new AccessDeniedException(
                laminPath.toUriString(), null,
                "LaminHub granted only '${cloudAccess.role}' access to storage ${storageRoot}. " +
                "Ask an instance admin for write permission, or publish to a storage you can write to."
            )
        }

        // Parse storage-root prefix from the root URI
        // e.g. s3://lamin-us-east-1/JwMEKs04D9WJ → prefix=JwMEKs04D9WJ
        String storagePrefix = new URI(storageRoot).path?.replaceFirst('^/', '')  // strip leading /

        String fullKey = storagePrefix ? "${storagePrefix}/${artifactKey}" : artifactKey
        if (!artifactKey) {
            fullKey = storagePrefix ?: ''
        }
        if (laminPath.subPath) {
            fullKey = "${fullKey}/${laminPath.subPath}"
        }

        // The file system refreshes its own credentials, so a transfer that outlives the
        // credentials it started with keeps going
        LaminS3FileSystemProvider s3Provider = FileHelper.getOrInstallProvider(LaminS3FileSystemProvider)
        LaminS3FileSystem s3Fs = s3Provider.getOrCreateFileSystem(storageRoot, { getCachedCloudAccess(hub, storageRoot) } as Supplier<CloudAccessResponse>, region)

        log.debug "Resolved ${laminPath} to lamin-s3://${s3Fs.bucketName}/${fullKey} (Lamin-managed credentials)"
        return new LaminS3Path(s3Fs, fullKey)
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
        LaminPath laminPath = toLaminPath(path)
        checkPublishable(laminPath)
        Path underlying = resolveToUnderlyingPath(laminPath, true)
        return Files.newOutputStream(underlying, options)
    }

    @Override
    SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        LaminPath laminPath = toLaminPath(path)
        boolean forWrite = options.any {
            it in [StandardOpenOption.WRITE, StandardOpenOption.APPEND,
                   StandardOpenOption.CREATE, StandardOpenOption.CREATE_NEW]
        }
        if (forWrite) {
            checkPublishable(laminPath)
        }
        Path underlying = resolveToUnderlyingPath(laminPath, forWrite)
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
        LaminPath laminPath = toLaminPath(dir)
        if (!laminPath.parsed.storage) {
            throw new UnsupportedOperationException("Creating directories in lamin:// artifact paths is not supported")
        }
        // Object stores have no directories, so there is nothing to create. Making this a
        // no-op is what lets Files.createDirectories -- and with it PublishDir -- succeed.
    }

    @Override
    void delete(Path path) throws IOException {
        LaminPath laminPath = toLaminPath(path)
        checkPublishable(laminPath)
        FileHelper.deletePath(resolveToUnderlyingPath(laminPath, true))
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

        LaminPath laminTarget = toLaminPath(target)
        checkPublishable(laminTarget)
        FileHelper.copyPath(source, resolveToUnderlyingPath(laminTarget, true), options)
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
        if (!(target instanceof LaminPath) || !((LaminPath) target).parsed.storage) {
            return false
        }
        return isLocalFileSystem(source) && isPublishEnabled()
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
        log.debug "upload: ${localFile} -> ${remoteDestination}"

        LaminPath laminPath = toLaminPath(remoteDestination)
        checkPublishable(laminPath)
        // copyPath re-dispatches to whichever provider actually owns the resolved path,
        // so the lamin-s3 uploader and nf-amazon are both reached the same way
        FileHelper.copyPath(localFile, resolveToUnderlyingPath(laminPath, true), options)
    }

    // ==================== Helper Methods ====================

    private static boolean isLocalFileSystem(Path path) {
        return path.fileSystem == java.nio.file.FileSystems.getDefault()
    }

    private static boolean isPublishEnabled() {
        LaminConfig config = LaminConnection.getInstance().getConfig()
        return config?.features?.publish_to_lamin_storage != false
    }

    /**
     * Refuse to write to anything but a publish target, and only when publishing is enabled.
     */
    private static void checkPublishable(LaminPath laminPath) throws IOException {
        if (!laminPath.parsed.storage) {
            throw new AccessDeniedException(
                laminPath.toUriString(), null,
                "artifact URIs are read-only; publish to 'lamin://${laminPath.owner}/${laminPath.instance}' instead"
            )
        }
        if (!isPublishEnabled()) {
            throw new AccessDeniedException(
                laminPath.toUriString(), null,
                "publishing to lamin:// is disabled (lamin.features.publish_to_lamin_storage = false)"
            )
        }
        if (laminPath.parsed.key?.startsWith(LaminUriParser.AUTO_KEY_PREFIX)) {
            throw new AccessDeniedException(
                laminPath.toUriString(), null,
                "'${LaminUriParser.AUTO_KEY_PREFIX}/' is reserved by LaminDB for auto-managed artifacts"
            )
        }
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
