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

import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

import ai.lamin.nf_lamin.LaminRunManager
import ai.lamin.nf_lamin.instance.Instance

/**
 * FileSystem implementation for Lamin URIs.
 *
 * Each LaminFileSystem is associated with a specific LaminDB instance
 * (identified by owner/name). It provides access to artifacts within
 * that instance via lamin:// URIs.
 *
 * This class delegates to LaminRunManager for hub and instance access,
 * sharing connections with the rest of the nf-lamin plugin.
 *
 * @author Lamin Labs
 */
@Slf4j
@CompileStatic
final class LaminFileSystem extends FileSystem implements Closeable {

    private final LaminFileSystemProvider provider
    private final String instanceSlug  // "owner/instance"
    private final String owner
    private final String instanceName

    private volatile boolean closed = false

    /**
     * Create a new LaminFileSystem.
     *
     * @param provider The provider that created this file system
     * @param instanceSlug The instance identifier in format "owner/name"
     */
    LaminFileSystem(LaminFileSystemProvider provider, String instanceSlug) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider cannot be null")
        }
        if (!instanceSlug?.contains('/')) {
            throw new IllegalArgumentException("Instance slug must be in format 'owner/name': ${instanceSlug}")
        }

        this.provider = provider
        this.instanceSlug = instanceSlug

        String[] parts = instanceSlug.split('/', 2)
        this.owner = parts[0]
        this.instanceName = parts[1]

        log.debug "Created LaminFileSystem for instance: ${instanceSlug}"
    }

    /**
     * Get the instance slug in format "owner/name".
     */
    String getInstanceSlug() {
        return instanceSlug
    }

    /**
     * Get the owner of the LaminDB instance.
     */
    String getOwner() {
        return owner
    }

    /**
     * Get the name of the LaminDB instance.
     */
    String getInstanceName() {
        return instanceName
    }

    /**
     * Get the Instance client for this file system's instance.
     * Delegates to LaminRunManager which manages the instance cache.
     */
    Instance getLaminInstance() {
        return provider.getInstance(owner, instanceName)
    }

    // ==================== FileSystem Interface Implementation ====================

    @Override
    FileSystemProvider provider() {
        return provider
    }

    @Override
    void close() throws IOException {
        closed = true
        provider.removeFileSystem(instanceSlug)
    }

    @Override
    boolean isOpen() {
        return !closed
    }

    @Override
    boolean isReadOnly() {
        // For now, lamin:// paths are read-only (artifact inputs)
        return true
    }

    @Override
    String getSeparator() {
        return LaminUriParser.SEP
    }

    @Override
    Iterable<Path> getRootDirectories() {
        // Root directories are not enumerable for Lamin
        return Collections.emptyList()
    }

    @Override
    Iterable<FileStore> getFileStores() {
        // File stores are not applicable
        return Collections.emptyList()
    }

    @Override
    Set<String> supportedFileAttributeViews() {
        return Collections.unmodifiableSet(['basic'] as Set)
    }

    @Override
    Path getPath(String first, String... more) {
        log.trace "LaminFileSystem.getPath: first=${first}, more=${more}"

        // Build the full path string
        String fullPath = first
        if (more != null && more.length > 0) {
            fullPath = fullPath + LaminUriParser.SEP + more.join(LaminUriParser.SEP)
        }

        // If it's already a full lamin:// URI, parse it
        if (fullPath.startsWith(LaminUriParser.SCHEME + ':')) {
            LaminUriParser parsed = LaminUriParser.parse(fullPath)
            return new LaminPath(this, parsed)
        }

        // Otherwise, treat it as a relative path/resource reference
        // This is used for relative path resolution
        // Create a minimal parser for the path
        throw new UnsupportedOperationException(
            "Relative paths are not supported. Use full lamin:// URI instead: ${fullPath}"
        )
    }

    /**
     * Get a path from a parsed URI.
     */
    LaminPath getPath(LaminUriParser parsed) {
        return new LaminPath(this, parsed)
    }

    @Override
    PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException("PathMatcher is not supported for LaminFileSystem")
    }

    @Override
    UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("UserPrincipalLookupService is not supported for LaminFileSystem")
    }

    @Override
    WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("WatchService is not supported for LaminFileSystem")
    }

    @Override
    String toString() {
        return "LaminFileSystem[${instanceSlug}]"
    }
}
