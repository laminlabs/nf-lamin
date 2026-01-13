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
import spock.lang.Unroll

import java.nio.file.Path

/**
 * Tests for LaminPath
 */
class LaminPathTest extends Specification {

    LaminFileSystemProvider provider
    LaminFileSystem fileSystem

    def setup() {
        provider = Mock(LaminFileSystemProvider)
        fileSystem = new LaminFileSystem(provider, 'laminlabs/lamindata')
    }

    // ==================== Constructor Tests ====================

    def "constructor should reject null fileSystem"() {
        when:
        new LaminPath(null, LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123'))

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor should reject null parsed URI"() {
        when:
        new LaminPath(fileSystem, null)

        then:
        thrown(IllegalArgumentException)
    }

    // ==================== Property Accessor Tests ====================

    def "should return correct owner"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.owner == 'laminlabs'
    }

    def "should return correct instance"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.instance == 'lamindata'
    }

    def "should return correct resourceType"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.resourceType == 'artifact'
    }

    def "should return correct resourceId"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.resourceId == 'uid123'
    }

    def "should return null subPath when none exists"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.subPath == null
    }

    def "should return correct subPath"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/subdir/file.txt')

        expect:
        path.subPath == 'subdir/file.txt'
    }

    // ==================== resolveToStorage Tests ====================

    def "resolveToStorage should delegate to provider"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def expectedPath = java.nio.file.Paths.get('/resolved/path')

        when:
        path.resolveToStorage()

        then:
        1 * provider.resolveToUnderlyingPath(path) >> expectedPath
    }

    // ==================== Path Interface - Basic Methods ====================

    def "getFileSystem should return the file system"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.fileSystem.is(fileSystem)
    }

    def "isAbsolute should always return true"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.isAbsolute()
    }

    // ==================== getRoot Tests ====================

    def "getRoot should return artifact without sub-path"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/subdir/file.txt')

        when:
        def root = path.root

        then:
        root.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123'
        root.subPath == null
    }

    def "getRoot should return self when already at root"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        def root = path.root

        then:
        root.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123'
    }

    // ==================== getFileName Tests ====================

    def "getFileName should return uid when no sub-path"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        def fileName = path.fileName

        then:
        fileName.toString() == 'uid123'
    }

    def "getFileName should return last component of sub-path"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/subdir/file.txt')

        when:
        def fileName = path.fileName

        then:
        fileName.toString() == 'file.txt'
    }

    // ==================== getParent Tests ====================

    def "getParent should return null when no sub-path"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.parent == null
    }

    def "getParent should return artifact when sub-path has one component"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/file.txt')

        when:
        def parent = path.parent

        then:
        parent.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123'
    }

    def "getParent should return parent directory"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/subdir/file.txt')

        when:
        def parent = path.parent

        then:
        parent.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123/subdir'
    }

    // ==================== getNameCount Tests ====================

    def "getNameCount should count all components without sub-path"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.nameCount == 4  // owner, instance, resourceType, resourceId
    }

    def "getNameCount should count all components with sub-path"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/subdir/file.txt')

        expect:
        path.nameCount == 6  // owner, instance, resourceType, resourceId, subdir, file.txt
    }

    // ==================== getName Tests ====================

    def "getName should throw UnsupportedOperationException"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        path.getName(0)

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains('getName()')
        e.message.contains('lamin://')
    }

    def "getName error message should include the path"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        path.getName(0)

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains('lamin://laminlabs/lamindata/artifact/uid123')
    }

    // ==================== subpath Tests ====================

    def "subpath should throw UnsupportedOperationException"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        path.subpath(0, 2)

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains('subpath()')
        e.message.contains('lamin://')
    }

    def "subpath error message should include the path"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/subdir/file.txt')

        when:
        path.subpath(0, 2)

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains('lamin://laminlabs/lamindata/artifact/uid123/subdir/file.txt')
    }

    // ==================== startsWith Tests ====================

    def "startsWith(Path) should return true for same path"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.startsWith(path)
    }

    def "startsWith(Path) should return false for non-LaminPath"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def localPath = java.nio.file.Paths.get('/local/path')

        expect:
        !path.startsWith(localPath)
    }

    def "startsWith(String) should work with URI prefix"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/subdir')

        expect:
        path.startsWith('lamin://laminlabs/lamindata')
    }

    // ==================== endsWith Tests ====================

    def "endsWith(Path) should return true for same path"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.endsWith(path)
    }

    def "endsWith(Path) should return false for non-LaminPath"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def localPath = java.nio.file.Paths.get('/local/path')

        expect:
        !path.endsWith(localPath)
    }

    def "endsWith(String) should work with suffix"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/file.txt')

        expect:
        path.endsWith('file.txt')
    }

    // ==================== normalize Tests ====================

    def "normalize should return self"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.normalize().is(path)
    }

    // ==================== resolve Tests ====================

    def "resolve(Path) should return self for null"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.resolve((Path) null).is(path)
    }

    def "resolve(Path) should return other for absolute path"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def other = createPath('lamin://other/instance/artifact/uid456')

        expect:
        path.resolve(other).is(other)
    }

    def "resolve(String) should return self for null"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.resolve((String) null).is(path)
    }

    def "resolve(String) should return self for empty string"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.resolve('').is(path)
    }

    def "resolve(String) should append relative path"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        def resolved = path.resolve('subdir/file.txt')

        then:
        resolved.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123/subdir/file.txt'
    }

    def "resolve(String) should parse absolute lamin URI"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        def resolved = path.resolve('lamin://other/instance/artifact/uid456')

        then:
        resolved.toUriString() == 'lamin://other/instance/artifact/uid456'
    }

    // ==================== resolveSibling Tests ====================

    def "resolveSibling(Path) should return parent for null"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/file.txt')

        when:
        def result = path.resolveSibling((Path) null)

        then:
        result.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123'
    }

    def "resolveSibling(Path) should return other when no parent"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def other = createPath('lamin://other/instance/artifact/uid456')

        expect:
        path.resolveSibling(other) == other
    }

    def "resolveSibling(String) should return parent for null"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/file.txt')

        when:
        def result = path.resolveSibling((String) null)

        then:
        result.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123'
    }

    def "resolveSibling(String) should return parent for empty string"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/file.txt')

        when:
        def result = path.resolveSibling('')

        then:
        result.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123'
    }

    def "resolveSibling(String) should resolve relative path with parent"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/subdir/file.txt')

        when:
        def result = path.resolveSibling('other.txt')

        then:
        result.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123/subdir/other.txt'
    }

    def "resolveSibling(String) should parse absolute lamin URI"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/file.txt')

        when:
        def result = path.resolveSibling('lamin://other/instance/artifact/uid456')

        then:
        result.toUriString() == 'lamin://other/instance/artifact/uid456'
    }

    def "resolveSibling(String) should handle path without parent"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        def result = path.resolveSibling('newfile.txt')

        then:
        result.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123/newfile.txt'
    }

    // ==================== relativize Tests ====================

    def "relativize should throw for non-LaminPath"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def localPath = java.nio.file.Paths.get('/local/path')

        when:
        path.relativize(localPath)

        then:
        thrown(IllegalArgumentException)
    }

    def "relativize should throw when other doesn't start with this"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def other = createPath('lamin://other/instance/artifact/uid456')

        when:
        path.relativize(other)

        then:
        thrown(IllegalArgumentException)
    }

    def "relativize should return empty path for same path"() {
        given:
        def base = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def other = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        def result = base.relativize(other)

        then:
        result.toString() == ''
    }

    def "relativize should return relative path for sub-path"() {
        given:
        def base = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def other = createPath('lamin://laminlabs/lamindata/artifact/uid123/subdir/file.txt')

        when:
        def result = base.relativize(other)

        then:
        result.toString() == 'subdir/file.txt'
    }

    def "relativize should return local Path type"() {
        given:
        def base = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def other = createPath('lamin://laminlabs/lamindata/artifact/uid123/file.txt')

        when:
        def result = base.relativize(other)

        then:
        !(result instanceof LaminPath)
    }

    // ==================== toUri Tests ====================

    def "toUri should return correct URI"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        def uri = path.toUri()

        then:
        uri.scheme == 'lamin'
        uri.toString() == 'lamin://laminlabs/lamindata/artifact/uid123'
    }

    // ==================== toAbsolutePath Tests ====================

    def "toAbsolutePath should return self"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.toAbsolutePath().is(path)
    }

    // ==================== toRealPath Tests ====================

    def "toRealPath should return self"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.toRealPath().is(path)
    }

    // ==================== toFile Tests ====================

    def "toFile should throw UnsupportedOperationException"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        path.toFile()

        then:
        thrown(UnsupportedOperationException)
    }

    // ==================== register Tests ====================

    def "register(WatchService, Kind[], Modifier[]) should throw UnsupportedOperationException"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        path.register(null, null, null)

        then:
        thrown(UnsupportedOperationException)
    }

    def "register(WatchService, Kind...) should throw UnsupportedOperationException"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        path.register(null)

        then:
        thrown(UnsupportedOperationException)
    }

    // ==================== iterator Tests ====================

    def "iterator should throw UnsupportedOperationException"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        path.iterator()

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains('iterator()')
        e.message.contains('lamin://')
    }

    def "iterator error message should include the path"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        path.iterator()

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains('lamin://laminlabs/lamindata/artifact/uid123')
    }

    // ==================== compareTo Tests ====================

    def "compareTo should return negative for non-LaminPath"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def localPath = java.nio.file.Paths.get('/local/path')

        expect:
        path.compareTo(localPath) < 0
    }

    def "compareTo should return 0 for equal paths"() {
        given:
        def path1 = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def path2 = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path1.compareTo(path2) == 0
    }

    def "compareTo should order paths alphabetically"() {
        given:
        def path1 = createPath('lamin://a/b/artifact/uid')
        def path2 = createPath('lamin://z/b/artifact/uid')

        expect:
        path1.compareTo(path2) < 0
        path2.compareTo(path1) > 0
    }

    // ==================== toString Tests ====================

    def "toString should return URI string"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.toString() == 'lamin://laminlabs/lamindata/artifact/uid123'
    }

    def "toString should return filename when isFileName is true"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123/file.txt')
        def fileName = path.fileName

        expect:
        fileName.toString() == 'file.txt'
    }

    // ==================== equals Tests ====================

    def "equals should return true for same instance"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path.equals(path)
    }

    def "equals should return false for null"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        !path.equals(null)
    }

    def "equals should return false for non-LaminPath"() {
        given:
        def path = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        !path.equals("not a path")
    }

    def "equals should return true for equal paths"() {
        given:
        def path1 = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def path2 = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path1.equals(path2)
    }

    def "equals should return false for different paths"() {
        given:
        def path1 = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def path2 = createPath('lamin://laminlabs/lamindata/artifact/uid456')

        expect:
        !path1.equals(path2)
    }

    // ==================== hashCode Tests ====================

    def "hashCode should be consistent for equal paths"() {
        given:
        def path1 = createPath('lamin://laminlabs/lamindata/artifact/uid123')
        def path2 = createPath('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        path1.hashCode() == path2.hashCode()
    }

    // ==================== Helper Methods ====================

    private LaminPath createPath(String uri) {
        def parsed = LaminUriParser.parse(uri)
        return new LaminPath(fileSystem, parsed)
    }
}
