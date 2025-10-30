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
import java.nio.file.spi.FileSystemProvider
import java.nio.file.Path
import java.nio.file.FileStore
import java.nio.file.PathMatcher
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.WatchService

@Slf4j
@CompileStatic
class LaminFileSystem extends FileSystem {

    private final LaminFileSystemProvider provider
    private final String originalUri
    private final String resolvedUri
    private final FileSystem underlyingFileSystem



    LaminFileSystem(LaminFileSystemProvider provider, String originalUri, String resolvedUri, FileSystem underlyingFileSystem) {
        this.provider = provider
        this.originalUri = originalUri
        this.resolvedUri = resolvedUri
        this.underlyingFileSystem = underlyingFileSystem
    }

    FileSystemProvider provider() {
        return provider
    }

    @Override
    void close() throws IOException {
        underlyingFileSystem.close()
    }

    boolean isOpen() {
        return underlyingFileSystem.isOpen()
    }

    boolean isReadOnly() {
        return underlyingFileSystem.isReadOnly()
    }

    String getSeparator() {
        return underlyingFileSystem.getSeparator()
    }

    Iterable<Path> getRootDirectories() {
        return underlyingFileSystem.getRootDirectories()
    }

    Iterable<FileStore> getFileStores() {
        return underlyingFileSystem.getFileStores()
    }

    Set<String> supportedFileAttributeViews() {
        return underlyingFileSystem.supportedFileAttributeViews()
    }

    Path getPath(String first, String... more) {
        // Should we relativize the path here based on originalUri and resolvedUri?
        return underlyingFileSystem.getPath(first, more)
    }

    PathMatcher getPathMatcher(String syntaxAndPattern) {
        // Should we adjust the pattern here based on originalUri and resolvedUri?
        return underlyingFileSystem.getPathMatcher(syntaxAndPattern)
    }

    UserPrincipalLookupService getUserPrincipalLookupService() {
        return underlyingFileSystem.getUserPrincipalLookupService()
    }

    WatchService newWatchService() throws IOException {
        return underlyingFileSystem.newWatchService()
    }

}

