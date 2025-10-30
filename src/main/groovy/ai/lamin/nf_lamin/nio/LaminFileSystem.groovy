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

    LaminFileSystem() {
    }

    FileSystemProvider provider() {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystem provider)")
    }

    @Override
    void close() throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystem close)")
    }

    boolean isOpen() {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystem isOpen)")
    }

    boolean isReadOnly() {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystem isReadOnly)")
    }

    String getSeparator() {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystem getSeparator)")
    }

    Iterable<Path> getRootDirectories() {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystem getRootDirectories)")
    }

    Iterable<FileStore> getFileStores() {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystem getFileStores)")
    }

    Set<String> supportedFileAttributeViews() {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystem supportedFileAttributeViews)")
    }

    Path getPath(String first, String... more) {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystem getPath)")
    }

    PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystem getPathMatcher)")
    }

    UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystem getUserPrincipalLookupService)")
    }

    WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystem newWatchService)")
    }

}

