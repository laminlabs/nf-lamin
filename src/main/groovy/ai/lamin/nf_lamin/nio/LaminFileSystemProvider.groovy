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

import java.nio.file.spi.FileSystemProvider
import nextflow.file.FileSystemTransferAware
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.channels.SeekableByteChannel
import java.nio.file.OpenOption
import java.nio.file.attribute.FileAttribute
import java.nio.file.DirectoryStream
import java.nio.file.CopyOption
import java.nio.file.FileStore
import java.nio.file.AccessMode
import java.nio.file.attribute.FileAttributeView
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

import java.net.URI
import nextflow.file.FileHelper

@Slf4j
@CompileStatic
class LaminFileSystemProvider extends FileSystemProvider implements FileSystemTransferAware {

    @Override
    String getScheme() {
        return 'lamin'
    }

    FileSystem newFileSystem(URI uri, Map<String,?> env)
        throws IOException {
        log.info("LaminFileSystemProvider.newFileSystem, uri {} env {}", uri, env)
        // throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider newFileSystem)")
        URI newUri = new URI('s3://lamindata/.lamindb/s3rtK8wIzJNKvg5Q0001.txt')
        FileSystem fs = FileHelper.getProviderFor(newUri.getScheme()).newFileSystem(newUri, env)
        return new LaminFileSystem(this, uri.toString(), newUri.toString(), fs)
    }

    FileSystem getFileSystem(URI uri) {
        log.info("LaminFileSystemProvider.getFileSystem, uri {}", uri)
        // throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider getFileSystem)")
        URI newUri = new URI('s3://lamindata/.lamindb/s3rtK8wIzJNKvg5Q0001.txt')
        FileSystem fs = FileHelper.getProviderFor(newUri.getScheme()).getFileSystem(newUri)
        return new LaminFileSystem(this, uri.toString(), newUri.toString(), fs)
    }

    Path getPath(URI uri) {
        log.info("LaminFileSystemProvider.getPath, uri ${uri}")
        // throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider getPath)")
        URI newUri = new URI('s3://lamindata/.lamindb/s3rtK8wIzJNKvg5Q0001.txt')
        return getFileSystem(newUri).getPath(newUri.getPath());
    }

    SeekableByteChannel newByteChannel(Path path,
        Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider newByteChannel)")
    }

    DirectoryStream<Path> newDirectoryStream(Path dir,
         DirectoryStream.Filter<? super Path> filter) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider newDirectoryStream)")
    }

    void createDirectory(Path dir, FileAttribute<?>... attrs)
        throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider createDirectory)")
    }

    void delete(Path path) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider delete)")
    }

    void copy(Path source, Path target, CopyOption... options)
        throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider copy)")
    }

    void move(Path source, Path target, CopyOption... options)
        throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider move)")
    }

    boolean isSameFile(Path path, Path path2)
        throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider isSameFile)")
    }

    boolean isHidden(Path path) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider isHidden)")
    }

    FileStore getFileStore(Path path) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider getFileStore)")
    }

    void checkAccess(Path path, AccessMode... modes)
        throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider checkAccess)")
    }

    <V extends FileAttributeView> V
        getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider getFileAttributeView)")
    }

    <A extends BasicFileAttributes> A
        readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider readAttributes)")
    }

    Map<String,Object> readAttributes(Path path, String attributes,
                                                      LinkOption... options)
        throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider readAttributes)")
    }

    void setAttribute(Path path, String attribute,
                                      Object value, LinkOption... options)
        throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider setAttribute)")
    }

    boolean canUpload(Path source, Path target) {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider canUpload)")
    }

    boolean canDownload(Path source, Path target) {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider canDownload)")
    }

    void download(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider download)")
    }

    void upload(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet (LaminFileSystemProvider upload)")
    }
}