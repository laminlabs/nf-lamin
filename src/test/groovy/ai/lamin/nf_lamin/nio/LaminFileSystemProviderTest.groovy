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

import spock.lang.Specification

import java.nio.file.FileSystemNotFoundException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.ProviderMismatchException

/**
 * Tests for LaminFileSystemProvider
 */
class LaminFileSystemProviderTest extends Specification {

    LaminFileSystemProvider provider

    def setup() {
        provider = new LaminFileSystemProvider()
    }

    // ==================== getScheme Tests ====================

    def "getScheme should return lamin"() {
        expect:
        provider.scheme == 'lamin'
    }

    // ==================== toLaminPath Tests ====================

    def "toLaminPath should return LaminPath for valid path"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def laminPath = provider.getPath(uri)

        when:
        def result = LaminFileSystemProvider.toLaminPath(laminPath)

        then:
        result.is(laminPath)
    }

    def "toLaminPath should throw ProviderMismatchException for non-LaminPath"() {
        given:
        def localPath = Paths.get('/local/path')

        when:
        LaminFileSystemProvider.toLaminPath(localPath)

        then:
        thrown(ProviderMismatchException)
    }

    def "toLaminPath should throw ProviderMismatchException for null"() {
        when:
        LaminFileSystemProvider.toLaminPath(null)

        then:
        thrown(ProviderMismatchException)
    }

    // ==================== newFileSystem Tests ====================

    def "newFileSystem should create new file system"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        def fs = provider.newFileSystem(uri, [:])

        then:
        fs instanceof LaminFileSystem
        fs.instanceSlug == 'laminlabs/lamindata'
    }

    def "newFileSystem should return existing file system for same instance"() {
        given:
        def uri1 = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def uri2 = new URI('lamin://laminlabs/lamindata/artifact/uid456')

        when:
        def fs1 = provider.newFileSystem(uri1, [:])
        def fs2 = provider.newFileSystem(uri2, [:])

        then:
        fs1.is(fs2)
    }

    def "newFileSystem should create different file systems for different instances"() {
        given:
        def uri1 = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def uri2 = new URI('lamin://other/instance/artifact/uid456')

        when:
        def fs1 = provider.newFileSystem(uri1, [:])
        def fs2 = provider.newFileSystem(uri2, [:])

        then:
        !fs1.is(fs2)
        fs1.instanceSlug == 'laminlabs/lamindata'
        fs2.instanceSlug == 'other/instance'
    }

    // ==================== getFileSystem Tests ====================

    def "getFileSystem should return existing file system"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def created = provider.newFileSystem(uri, [:])

        when:
        def retrieved = provider.getFileSystem(uri)

        then:
        retrieved.is(created)
    }

    def "getFileSystem should throw FileSystemNotFoundException for non-existent"() {
        given:
        def uri = new URI('lamin://nonexistent/instance/artifact/uid123')

        when:
        provider.getFileSystem(uri)

        then:
        thrown(FileSystemNotFoundException)
    }

    // ==================== getOrCreateFileSystem Tests ====================

    def "getOrCreateFileSystem should create new file system"() {
        given:
        def uri = new URI('lamin://newowner/newinstance/artifact/uid123')

        when:
        def fs = provider.getOrCreateFileSystem(uri)

        then:
        fs instanceof LaminFileSystem
        fs.instanceSlug == 'newowner/newinstance'
    }

    def "getOrCreateFileSystem should return existing file system"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def created = provider.newFileSystem(uri, [:])

        when:
        def retrieved = provider.getOrCreateFileSystem(uri)

        then:
        retrieved.is(created)
    }

    // ==================== getPath Tests ====================

    def "getPath should create LaminPath"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        def path = provider.getPath(uri)

        then:
        path instanceof LaminPath
        path.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123'
    }

    def "getPath should create LaminPath with sub-path"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123/subdir/file.txt')

        when:
        def path = provider.getPath(uri)

        then:
        path instanceof LaminPath
        ((LaminPath) path).subPath == 'subdir/file.txt'
    }

    // ==================== removeFileSystem Tests ====================

    def "removeFileSystem should remove from cache"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        provider.newFileSystem(uri, [:])

        when:
        provider.removeFileSystem('laminlabs/lamindata')
        provider.getFileSystem(uri)

        then:
        thrown(FileSystemNotFoundException)
    }

    // ==================== Unsupported Write Operations Tests ====================

    def "newOutputStream should throw UnsupportedOperationException"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def path = provider.getPath(uri)

        when:
        provider.newOutputStream(path)

        then:
        thrown(UnsupportedOperationException)
    }

    def "createDirectory should throw UnsupportedOperationException"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def path = provider.getPath(uri)

        when:
        provider.createDirectory(path)

        then:
        thrown(UnsupportedOperationException)
    }

    def "delete should throw UnsupportedOperationException"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def path = provider.getPath(uri)

        when:
        provider.delete(path)

        then:
        thrown(UnsupportedOperationException)
    }

    def "move should throw UnsupportedOperationException"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def source = provider.getPath(uri)
        def target = Paths.get('/local/target')

        when:
        provider.move(source, target)

        then:
        thrown(UnsupportedOperationException)
    }

    def "copy to lamin path should throw UnsupportedOperationException"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def target = provider.getPath(uri)
        def source = Paths.get('/local/source')

        when:
        provider.copy(source, target)

        then:
        thrown(UnsupportedOperationException)
    }

    def "getFileStore should throw UnsupportedOperationException"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def path = provider.getPath(uri)

        when:
        provider.getFileStore(path)

        then:
        thrown(UnsupportedOperationException)
    }

    def "setAttribute should throw UnsupportedOperationException"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def path = provider.getPath(uri)

        when:
        provider.setAttribute(path, 'attr', 'value')

        then:
        thrown(UnsupportedOperationException)
    }

    def "upload should throw UnsupportedOperationException"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def target = provider.getPath(uri)
        def source = Paths.get('/local/source')

        when:
        provider.upload(source, target)

        then:
        thrown(UnsupportedOperationException)
    }

    // ==================== isSameFile Tests ====================

    def "isSameFile should return true for equal LaminPaths"() {
        given:
        def uri1 = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def uri2 = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def path1 = provider.getPath(uri1)
        def path2 = provider.getPath(uri2)

        expect:
        provider.isSameFile(path1, path2)
    }

    def "isSameFile should return false for different LaminPaths"() {
        given:
        def uri1 = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def uri2 = new URI('lamin://laminlabs/lamindata/artifact/uid456')
        def path1 = provider.getPath(uri1)
        def path2 = provider.getPath(uri2)

        expect:
        !provider.isSameFile(path1, path2)
    }

    def "isSameFile should return false when mixing with non-LaminPath"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def laminPath = provider.getPath(uri)
        def localPath = Paths.get('/local/path')

        expect:
        !provider.isSameFile(laminPath, localPath)
        !provider.isSameFile(localPath, laminPath)
    }

    // ==================== isHidden Tests ====================

    def "isHidden should return false"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def path = provider.getPath(uri)

        expect:
        !provider.isHidden(path)
    }

    // ==================== FileSystemTransferAware Tests ====================

    def "canUpload should return false"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def target = provider.getPath(uri)
        def source = Paths.get('/local/source')

        expect:
        !provider.canUpload(source, target)
    }

    def "canDownload should return true for LaminPath to local path"() {
        given:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid123')
        def source = provider.getPath(uri)
        def target = Paths.get('/local/target')

        expect:
        provider.canDownload(source, target)
    }

    def "canDownload should return false for non-LaminPath source"() {
        given:
        def source = Paths.get('/local/source')
        def target = Paths.get('/local/target')

        expect:
        !provider.canDownload(source, target)
    }
}
