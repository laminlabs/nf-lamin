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

/**
 * Tests for LaminFileSystem
 */
class LaminFileSystemTest extends Specification {

    LaminFileSystemProvider provider

    def setup() {
        provider = Mock(LaminFileSystemProvider)
    }

    // ==================== Constructor Tests ====================

    def "constructor should reject null provider"() {
        when:
        new LaminFileSystem(null, 'owner/instance')

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor should reject null instance slug"() {
        when:
        new LaminFileSystem(provider, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor should reject empty instance slug"() {
        when:
        new LaminFileSystem(provider, '')

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor should reject instance slug without slash"() {
        when:
        new LaminFileSystem(provider, 'invalid')

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor should accept valid instance slug"() {
        when:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        then:
        notThrown(Exception)
        fs.instanceSlug == 'owner/instance'
    }

    // ==================== Property Accessor Tests ====================

    def "getInstanceSlug should return the instance slug"() {
        given:
        def fs = new LaminFileSystem(provider, 'laminlabs/lamindata')

        expect:
        fs.instanceSlug == 'laminlabs/lamindata'
    }

    def "getOwner should return the owner"() {
        given:
        def fs = new LaminFileSystem(provider, 'laminlabs/lamindata')

        expect:
        fs.owner == 'laminlabs'
    }

    def "getInstanceName should return the instance name"() {
        given:
        def fs = new LaminFileSystem(provider, 'laminlabs/lamindata')

        expect:
        fs.instanceName == 'lamindata'
    }

    // ==================== FileSystem Interface Tests ====================

    def "provider should return the provider"() {
        given:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        expect:
        fs.provider().is(provider)
    }

    def "isOpen should return true initially"() {
        given:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        expect:
        fs.isOpen()
    }

    def "isOpen should return false after close"() {
        given:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        when:
        fs.close()

        then:
        !fs.isOpen()
    }

    def "close should notify provider"() {
        given:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        when:
        fs.close()

        then:
        1 * provider.removeFileSystem('owner/instance')
    }

    def "isReadOnly should return true"() {
        given:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        expect:
        fs.isReadOnly()
    }

    def "getSeparator should return forward slash"() {
        given:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        expect:
        fs.separator == '/'
    }

    def "getRootDirectories should return empty list"() {
        given:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        when:
        def roots = fs.rootDirectories

        then:
        !roots.iterator().hasNext()
    }

    def "getFileStores should return empty list"() {
        given:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        when:
        def stores = fs.fileStores

        then:
        !stores.iterator().hasNext()
    }

    def "supportedFileAttributeViews should contain basic"() {
        given:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        expect:
        fs.supportedFileAttributeViews().contains('basic')
    }

    // ==================== getPath Tests ====================

    def "getPath should parse full lamin URI"() {
        given:
        def fs = new LaminFileSystem(provider, 'laminlabs/lamindata')

        when:
        def path = fs.getPath('lamin://laminlabs/lamindata/artifact/uid123')

        then:
        path instanceof LaminPath
        path.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123'
    }

    def "getPath should join multiple parts"() {
        given:
        def fs = new LaminFileSystem(provider, 'laminlabs/lamindata')

        when:
        def path = fs.getPath('lamin://laminlabs/lamindata/artifact/uid123', 'subdir', 'file.txt')

        then:
        path instanceof LaminPath
        path.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123/subdir/file.txt'
    }

    def "getPath should throw for relative paths"() {
        given:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        when:
        fs.getPath('relative/path')

        then:
        thrown(UnsupportedOperationException)
    }

    def "getPath(LaminUriParser) should create path"() {
        given:
        def fs = new LaminFileSystem(provider, 'laminlabs/lamindata')
        def parsed = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        def path = fs.getPath(parsed)

        then:
        path instanceof LaminPath
        path.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123'
    }

    // ==================== Unsupported Operations Tests ====================

    def "getPathMatcher should throw UnsupportedOperationException"() {
        given:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        when:
        fs.getPathMatcher('glob:*')

        then:
        thrown(UnsupportedOperationException)
    }

    def "getUserPrincipalLookupService should throw UnsupportedOperationException"() {
        given:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        when:
        fs.getUserPrincipalLookupService()

        then:
        thrown(UnsupportedOperationException)
    }

    def "newWatchService should throw UnsupportedOperationException"() {
        given:
        def fs = new LaminFileSystem(provider, 'owner/instance')

        when:
        fs.newWatchService()

        then:
        thrown(UnsupportedOperationException)
    }

    // ==================== toString Tests ====================

    def "toString should include instance slug"() {
        given:
        def fs = new LaminFileSystem(provider, 'laminlabs/lamindata')

        expect:
        fs.toString() == 'LaminFileSystem[laminlabs/lamindata]'
    }
}
