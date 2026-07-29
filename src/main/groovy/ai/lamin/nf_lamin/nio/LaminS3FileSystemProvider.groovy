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
import java.nio.file.NoSuchFileException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.ProviderMismatchException
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider

import java.util.function.Supplier

import nextflow.file.CopyOptions
import nextflow.file.FileSystemTransferAware

import ai.lamin.nf_lamin.hub.CloudAccessResponse

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client
import software.amazon.awssdk.services.s3.model.CommonPrefix
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Object

/**
 * FileSystemProvider for lamin-s3:// URIs.
 *
 * Provides access to S3 objects using temporary session credentials (AccessKeyId +
 * SecretAccessKey + SessionToken) obtained from LaminHub via {@code getCloudAccess()}.
 *
 * URIs are of the form: {@code lamin-s3://bucket-name/path/to/object}
 *
 * This provider is separate from the nf-amazon S3 provider so that temporary session
 * credentials (including a SessionToken) can be used without interfering with the user's
 * existing AWS configuration.
 *
 * Credential federation flow:
 * 1. LaminFileSystemProvider resolves a lamin:// URI to get storageRoot + artifactKey
 * 2. It calls LaminHub.getCloudAccess(storageRoot) to get temporary STS credentials
 * 3. It calls LaminS3FileSystemProvider.getOrCreateFileSystem(bucket, creds...)
 * 4. Nextflow stages the file via lamin-s3://bucket/key using those credentials
 */
@Slf4j
@CompileStatic
class LaminS3FileSystemProvider extends FileSystemProvider implements FileSystemTransferAware {

    static final String SCHEME = 'lamin-s3'

    // Cache: storageRoot -> file system (keyed by storageRoot; credentials are scoped per storageRoot)
    private final Map<String, LaminS3FileSystem> fileSystems = Collections.synchronizedMap(new LinkedHashMap<String, LaminS3FileSystem>())

    @Override
    String getScheme() {
        return SCHEME
    }

    /**
     * Get or create an S3 file system for the given storage root using temporary session credentials.
     *
     * Credentials from LaminHub are scoped to a specific storage root (e.g.
     * {@code s3://lamin-us-east-1/JwMEKs04D9WJ}), not to the entire bucket. Multiple
     * storage roots may share the same bucket but require separate credentials. The cache
     * is therefore keyed by {@code storageRoot}.
     *
     * If a file system already exists for this storageRoot and was created with the same
     * AccessKeyId, it is returned as-is. Otherwise a new S3 client is created with
     * the provided session credentials and a new file system is installed.
     *
     * @param storageRoot The full storage root URI (e.g. {@code s3://bucket/prefix}), used as cache key and to derive the bucket name
     * @param accessKeyId     Temporary access key ID from STS
     * @param secretAccessKey Temporary secret access key from STS
     * @param sessionToken    Temporary session token from STS
     * @return The LaminS3FileSystem for this storageRoot
     */
    LaminS3FileSystem getOrCreateFileSystem(String storageRoot, String accessKeyId, String secretAccessKey, String sessionToken) {
        synchronized (fileSystems) {
            LaminS3FileSystem existing = fileSystems.get(storageRoot)
            if (existing != null && existing.accessKeyId == accessKeyId) {
                return existing
            }

            // Create a new S3 client with the temporary session credentials
            AwsSessionCredentials credentials = AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken)
            AwsS3Client s3Client = createS3Client(StaticCredentialsProvider.create(credentials), null)

            LaminS3FileSystem fs = new LaminS3FileSystem(this, storageRoot, s3Client, accessKeyId)
            fileSystems.put(storageRoot, fs)
            log.debug "Created LaminS3FileSystem for storageRoot '${storageRoot}' with accessKeyId ending in '${accessKeyId.takeRight(4)}'"
            return fs
        }
    }

    /**
     * Get or create an S3 file system whose credentials are refreshed on demand.
     *
     * Unlike the static variant above, the returned file system is never invalidated: the
     * SDK asks the supplier for credentials on every request, so a long-running transfer
     * survives the expiry of the credentials it started with.
     *
     * @param storageRoot The full storage root URI (e.g. {@code s3://bucket/prefix})
     * @param supplier Supplies current LaminHub credentials for this storage root
     * @param region Storage region, or null to let cross-region access sort it out
     * @return The LaminS3FileSystem for this storageRoot
     */
    LaminS3FileSystem getOrCreateFileSystem(String storageRoot, Supplier<CloudAccessResponse> supplier, String region) {
        synchronized (fileSystems) {
            LaminS3FileSystem existing = fileSystems.get(storageRoot)
            if (existing != null) {
                return existing
            }

            CloudAccessResponse access = supplier.get()
            AwsS3Client s3Client = createS3Client(new LaminCloudCredentialsProvider(supplier), region)

            LaminS3FileSystem fs = new LaminS3FileSystem(this, storageRoot, s3Client, access?.accessKeyId, access?.role)
            fileSystems.put(storageRoot, fs)
            log.debug "Created LaminS3FileSystem for storageRoot '${storageRoot}' (role=${access?.role}, region=${region})"
            return fs
        }
    }

    void removeFileSystem(String storageRoot) {
        fileSystems.remove(storageRoot)
    }

    /**
     * Creates an AWS S3 client for the given credentials.
     * Protected to allow test subclasses to inject mock clients.
     */
    protected AwsS3Client createS3Client(AwsCredentialsProvider credentialsProvider, String region) {
        return AwsS3Client.builder()
            .crossRegionAccessEnabled(true)
            .region(region ? Region.of(region) : Region.US_EAST_1)
            .credentialsProvider(credentialsProvider)
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build()
    }

    // ==================== FileSystemProvider Core Methods ====================

    @Override
    FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        String storageRoot = env.get('storageRoot') as String ?: uri.toString()
        String accessKeyId = env.get('accessKeyId') as String
        String secretAccessKey = env.get('secretAccessKey') as String
        String sessionToken = env.get('sessionToken') as String
        return getOrCreateFileSystem(storageRoot, accessKeyId, secretAccessKey, sessionToken)
    }

    @Override
    FileSystem getFileSystem(URI uri) {
        String bucket = uri.host
        // The cache is keyed by storageRoot; scan for a matching bucket
        LaminS3FileSystem fs = (LaminS3FileSystem) fileSystems.values().find { it.bucketName == bucket }
        if (fs == null) {
            throw new FileSystemNotFoundException("No lamin-s3 file system for bucket: ${bucket}")
        }
        return fs
    }

    @Override
    Path getPath(URI uri) {
        String bucket = uri.host
        // The cache is keyed by storageRoot; scan for a matching bucket
        LaminS3FileSystem fs = (LaminS3FileSystem) fileSystems.values().find { it.bucketName == bucket }
        if (fs == null) {
            throw new FileSystemNotFoundException("No lamin-s3 file system for bucket '${bucket}'. Call newFileSystem() or getOrCreateFileSystem() first.")
        }
        String key = uri.path?.replaceFirst('^/', '') ?: ''
        return new LaminS3Path(fs, key)
    }

    // ==================== File Operations ====================

    @Override
    InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        LaminS3Path s3Path = toLaminS3Path(path)
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(s3Path.bucket)
                .key(s3Path.key)
                .build()
            return ((LaminS3FileSystem) s3Path.fileSystem).s3Client.getObject(request)
        } catch (NoSuchKeyException e) {
            throw new NoSuchFileException(path.toString())
        } catch (Exception e) {
            throw new IOException("Failed to open input stream for ${path}", e)
        }
    }

    @Override
    OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        LaminS3Path s3Path = toLaminS3Path(path)
        Set<OpenOption> opts = options as Set<OpenOption>
        if (!opts) {
            // Same defaults java.nio.file.Files applies for an unqualified open
            opts = [StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE] as Set<OpenOption>
        }

        if (opts.contains(StandardOpenOption.APPEND)) {
            // S3 has no append; fall back to the byte-channel route, which reads the object
            // back first
            return super.newOutputStream(path, options)
        }
        if (opts.contains(StandardOpenOption.READ)) {
            throw new IllegalArgumentException("READ not allowed when opening ${path} for writing")
        }

        checkWritable(s3Path)
        boolean exists = exists(s3Path)
        if (opts.contains(StandardOpenOption.CREATE_NEW) && exists) {
            throw new FileAlreadyExistsException(path.toString())
        }
        if (!exists && !opts.contains(StandardOpenOption.CREATE) && !opts.contains(StandardOpenOption.CREATE_NEW)) {
            throw new NoSuchFileException(path.toString())
        }

        return new LaminS3OutputStream(uploaderFor(s3Path), s3Path.bucket, s3Path.key)
    }

    @Override
    SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        LaminS3Path s3Path = toLaminS3Path(path)
        boolean forWrite = options.any {
            it in [StandardOpenOption.WRITE, StandardOpenOption.APPEND,
                   StandardOpenOption.CREATE, StandardOpenOption.CREATE_NEW]
        }
        if (forWrite) {
            checkWritable(s3Path)
        }
        if (options.contains(StandardOpenOption.CREATE_NEW) && exists(s3Path)) {
            throw new FileAlreadyExistsException(path.toString())
        }

        // Stage the object locally, work on it there, and put it back on close
        Path tempFile = Files.createTempFile('nf-lamin-', '.channel')
        try {
            if (!options.contains(StandardOpenOption.CREATE_NEW)) {
                copyToLocal(s3Path, tempFile)
            }
        }
        catch (NoSuchFileException e) {
            if (!forWrite) {
                Files.deleteIfExists(tempFile)
                throw e
            }
        }

        Set<OpenOption> localOptions = new HashSet<OpenOption>(options)
        localOptions.remove(StandardOpenOption.CREATE_NEW)
        localOptions.add(StandardOpenOption.CREATE)
        localOptions.remove(StandardOpenOption.TRUNCATE_EXISTING)

        SeekableByteChannel channel = Files.newByteChannel(tempFile, localOptions)
        return new LaminS3ByteChannel(channel, tempFile, forWrite ? uploaderFor(s3Path) : null, s3Path)
    }

    @Override
    DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        LaminS3Path s3Dir = toLaminS3Path(dir)
        String prefix = s3Dir.key ? s3Dir.key.replaceFirst('/$', '') + '/' : ''
        LaminS3FileSystem fs = (LaminS3FileSystem) s3Dir.fileSystem

        Set<String> names = new LinkedHashSet<String>()
        try {
            String continuationToken = null
            while (true) {
                ListObjectsV2Response page = fs.s3Client.listObjectsV2(
                    ListObjectsV2Request.builder()
                        .bucket(s3Dir.bucket)
                        .prefix(prefix)
                        .delimiter(LaminS3Path.SEP)
                        .continuationToken(continuationToken)
                        .build()
                )
                page.contents().each { S3Object object ->
                    String name = object.key().substring(prefix.length())
                    if (name) {
                        names.add(name)
                    }
                }
                page.commonPrefixes().each { CommonPrefix common ->
                    String name = common.prefix().substring(prefix.length()).replaceFirst('/$', '')
                    if (name) {
                        names.add(name)
                    }
                }
                if (!page.isTruncated()) {
                    break
                }
                continuationToken = page.nextContinuationToken()
            }
        }
        catch (Exception e) {
            throw new IOException("Failed to list ${dir}", e)
        }

        List<Path> entries = names.collect { String name -> (Path) new LaminS3Path(fs, prefix + name) }
        if (filter != null) {
            entries = entries.findAll { Path p -> filter.accept(p) }
        }
        return new LaminS3DirectoryStream(entries)
    }

    @Override
    void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        // S3 has no directories. Creating zero-byte markers would litter Lamin-managed
        // storage with objects LaminDB never created, so this is deliberately a no-op.
        toLaminS3Path(dir)
    }

    @Override
    void delete(Path path) throws IOException {
        LaminS3Path s3Path = toLaminS3Path(path)
        checkWritable(s3Path)
        if (!exists(s3Path)) {
            throw new NoSuchFileException(path.toString())
        }
        try {
            AwsS3Client client = ((LaminS3FileSystem) s3Path.fileSystem).s3Client
            client.deleteObject(DeleteObjectRequest.builder().bucket(s3Path.bucket).key(s3Path.key).build())
            // Also drop the directory marker some tools write alongside the object
            client.deleteObject(DeleteObjectRequest.builder().bucket(s3Path.bucket).key(s3Path.key + LaminS3Path.SEP).build())
        }
        catch (Exception e) {
            throw new IOException("Failed to delete ${path}", e)
        }
    }

    @Override
    void copy(Path source, Path target, CopyOption... options) throws IOException {
        LaminS3Path s3Source = toLaminS3Path(source)
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(s3Source.bucket)
                .key(s3Source.key)
                .build()
            InputStream inputStream = ((LaminS3FileSystem) s3Source.fileSystem).s3Client.getObject(request)
            try {
                Files.copy(inputStream, target, options)
            } finally {
                inputStream.close()
            }
        } catch (NoSuchKeyException e) {
            throw new NoSuchFileException(source.toString())
        }
    }

    @Override
    void move(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException("Moving ${SCHEME}:// paths is not supported")
    }

    @Override
    boolean isSameFile(Path path1, Path path2) throws IOException {
        return path1.equals(path2)
    }

    @Override
    boolean isHidden(Path path) throws IOException {
        return false
    }

    @Override
    FileStore getFileStore(Path path) throws IOException {
        throw new UnsupportedOperationException("FileStore not supported for ${SCHEME}:// paths")
    }

    @Override
    void checkAccess(Path path, AccessMode... modes) throws IOException {
        LaminS3Path s3Path = toLaminS3Path(path)
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(s3Path.bucket)
                .key(s3Path.key)
                .build()
            ((LaminS3FileSystem) s3Path.fileSystem).s3Client.headObject(request)
        } catch (NoSuchKeyException e) {
            throw new NoSuchFileException(path.toString())
        } catch (Exception e) {
            throw new AccessDeniedException(path.toString(), null, e.message)
        }
    }

    @Override
    <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return null
    }

    @Override
    <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        LaminS3Path s3Path = toLaminS3Path(path)
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(s3Path.bucket)
                .key(s3Path.key)
                .build()
            HeadObjectResponse response = ((LaminS3FileSystem) s3Path.fileSystem).s3Client.headObject(request)
            return (A) new LaminS3FileAttributes(response)
        } catch (NoSuchKeyException e) {
            throw new NoSuchFileException(path.toString())
        } catch (Exception e) {
            throw new IOException("Failed to read attributes for ${path}", e)
        }
    }

    @Override
    Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return Collections.emptyMap()
    }

    @Override
    void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Setting attributes on ${SCHEME}:// paths is not supported")
    }

    // ==================== FileSystemTransferAware ====================

    @Override
    boolean canUpload(Path source, Path target) {
        return isLocalFileSystem(source) && target instanceof LaminS3Path && !target.fileSystem.isReadOnly()
    }

    @Override
    boolean canDownload(Path source, Path target) {
        return source instanceof LaminS3Path && isLocalFileSystem(target)
    }

    @Override
    void download(Path remoteFile, Path localDestination, CopyOption... options) throws IOException {
        log.debug "download: ${remoteFile} -> ${localDestination}"
        LaminS3Path s3Path = toLaminS3Path(remoteFile)

        CopyOptions opts = CopyOptions.parse(options)
        if (opts.replaceExisting()) {
            Files.deleteIfExists(localDestination)
        }

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(s3Path.bucket)
                .key(s3Path.key)
                .build()
            InputStream inputStream = ((LaminS3FileSystem) s3Path.fileSystem).s3Client.getObject(request)
            try {
                Files.copy(inputStream, localDestination)
            } finally {
                inputStream.close()
            }
        } catch (NoSuchKeyException e) {
            throw new NoSuchFileException(remoteFile.toString())
        }
    }

    @Override
    void upload(Path localFile, Path remoteDestination, CopyOption... options) throws IOException {
        log.debug "upload: ${localFile} -> ${remoteDestination}"
        LaminS3Path target = toLaminS3Path(remoteDestination)
        checkWritable(target)

        CopyOptions opts = CopyOptions.parse(options)
        if (!opts.replaceExisting() && exists(target)) {
            throw new FileAlreadyExistsException(remoteDestination.toString())
        }

        LaminS3Uploader uploader = uploaderFor(target)
        if (!Files.isDirectory(localFile)) {
            uploader.upload(localFile, target.bucket, target.key)
            return
        }

        String baseKey = target.key.replaceFirst('/$', '')
        Files.walk(localFile).each { Path source ->
            if (Files.isDirectory(source)) {
                return
            }
            String relative = localFile.relativize(source).toString().replace(File.separator, LaminS3Path.SEP)
            uploader.upload(source, target.bucket, "${baseKey}/${relative}")
        }
    }

    // ==================== Helpers ====================

    private static LaminS3Path toLaminS3Path(Path path) {
        if (path instanceof LaminS3Path) {
            return (LaminS3Path) path
        }
        throw new ProviderMismatchException("Not a LaminS3Path: ${path?.class?.name}")
    }

    private static boolean isLocalFileSystem(Path path) {
        return path.fileSystem == java.nio.file.FileSystems.getDefault()
    }

    /**
     * Uploader for the file system a path belongs to. Protected so tests can control the
     * multipart threshold.
     */
    protected LaminS3Uploader uploaderFor(LaminS3Path path) {
        return new LaminS3Uploader(((LaminS3FileSystem) path.fileSystem).s3Client)
    }

    private static void checkWritable(LaminS3Path path) throws IOException {
        LaminS3FileSystem fs = (LaminS3FileSystem) path.fileSystem
        if (fs.isReadOnly()) {
            throw new AccessDeniedException(
                path.toString(), null,
                "LaminHub granted only '${fs.role ?: 'read'}' access to storage ${fs.storageRoot}"
            )
        }
    }

    private boolean exists(LaminS3Path path) {
        try {
            checkAccess(path)
            return true
        }
        catch (NoSuchFileException e) {
            return false
        }
        catch (IOException e) {
            return false
        }
    }

    private void copyToLocal(LaminS3Path source, Path target) throws IOException {
        InputStream stream = newInputStream(source)
        try {
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
        }
        finally {
            stream.close()
        }
    }

    /**
     * A byte channel backed by a local temp file, uploaded back to S3 on close when the
     * channel was opened for writing.
     */
    @CompileStatic
    private static class LaminS3ByteChannel implements SeekableByteChannel {

        @Delegate
        private final SeekableByteChannel delegate
        private final Path tempFile
        private final LaminS3Uploader uploader
        private final LaminS3Path target

        LaminS3ByteChannel(SeekableByteChannel delegate, Path tempFile, LaminS3Uploader uploader, LaminS3Path target) {
            this.delegate = delegate
            this.tempFile = tempFile
            this.uploader = uploader
            this.target = target
        }

        @Override
        void close() throws IOException {
            if (!delegate.isOpen()) {
                return
            }
            try {
                delegate.close()
                if (uploader != null) {
                    uploader.upload(tempFile, target.bucket, target.key)
                }
            }
            finally {
                Files.deleteIfExists(tempFile)
            }
        }
    }

    /**
     * A directory stream over an eagerly listed set of entries.
     */
    @CompileStatic
    private static class LaminS3DirectoryStream implements DirectoryStream<Path> {

        private final List<Path> entries

        LaminS3DirectoryStream(List<Path> entries) {
            this.entries = entries
        }

        @Override
        Iterator<Path> iterator() {
            return entries.iterator()
        }

        @Override
        void close() throws IOException {
            // nothing to release, the listing is already complete
        }
    }
}
