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

class LaminUriParserTest extends Specification {

    def "should parse basic artifact URI"() {
        when:
        def parsed = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/s3rtK8wIzJNKvg5Q')

        then:
        parsed.owner == 'laminlabs'
        parsed.instance == 'lamindata'
        parsed.resourceType == 'artifact'
        parsed.resourceId == 's3rtK8wIzJNKvg5Q'
        parsed.subPath == null
        !parsed.hasSubPath()
    }

    def "should parse artifact URI with sub-path"() {
        when:
        def parsed = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/s3rtK8wIzJNKvg5Q/subdir/file.txt')

        then:
        parsed.owner == 'laminlabs'
        parsed.instance == 'lamindata'
        parsed.resourceType == 'artifact'
        parsed.resourceId == 's3rtK8wIzJNKvg5Q'
        parsed.subPath == 'subdir/file.txt'
        parsed.hasSubPath()
    }

    def "should parse URI object"() {
        when:
        def uri = new URI('lamin://laminlabs/lamindata/artifact/uid12345678')
        def parsed = LaminUriParser.parse(uri)

        then:
        parsed.owner == 'laminlabs'
        parsed.instance == 'lamindata'
        parsed.resourceType == 'artifact'
        parsed.resourceId == 'uid12345678'
    }

    def "should return correct instance slug"() {
        when:
        def parsed = LaminUriParser.parse('lamin://myorg/myinstance/artifact/uid123')

        then:
        parsed.instanceSlug == 'myorg/myinstance'
    }

    def "should convert back to URI string"() {
        given:
        def original = 'lamin://laminlabs/lamindata/artifact/s3rtK8wIzJNKvg5Q'

        when:
        def parsed = LaminUriParser.parse(original)

        then:
        parsed.toUriString() == original
    }

    def "should convert URI with sub-path back to string"() {
        given:
        def original = 'lamin://laminlabs/lamindata/artifact/uid123/path/to/file.txt'

        when:
        def parsed = LaminUriParser.parse(original)

        then:
        parsed.toUriString() == original
    }

    def "should get filename from URI without sub-path"() {
        when:
        def parsed = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123')

        then:
        parsed.fileName == 'uid123'
    }

    def "should get filename from URI with sub-path"() {
        when:
        def parsed = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123/path/to/file.txt')

        then:
        parsed.fileName == 'file.txt'
    }

    def "should get parent from URI with sub-path"() {
        when:
        def parsed = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123/path/to/file.txt')
        def parent = parsed.parent

        then:
        parent.subPath == 'path/to'
        parent.resourceId == 'uid123'
    }

    def "should get parent from URI with single sub-path component"() {
        when:
        def parsed = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123/file.txt')
        def parent = parsed.parent

        then:
        parent.subPath == null
        parent.resourceId == 'uid123'
    }

    def "should return null parent for URI without sub-path"() {
        when:
        def parsed = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123')

        then:
        parsed.parent == null
    }

    def "should append sub-path"() {
        given:
        def parsed = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123')

        when:
        def withPath = parsed.withSubPath('subdir/file.txt')

        then:
        withPath.subPath == 'subdir/file.txt'
        withPath.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123/subdir/file.txt'
    }

    def "should append to existing sub-path"() {
        given:
        def parsed = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123/existing')

        when:
        def withPath = parsed.withSubPath('more/path')

        then:
        withPath.subPath == 'existing/more/path'
    }

    def "should remove sub-path"() {
        given:
        def parsed = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123/path/to/file.txt')

        when:
        def withoutPath = parsed.withoutSubPath()

        then:
        withoutPath.subPath == null
        withoutPath.toUriString() == 'lamin://laminlabs/lamindata/artifact/uid123'
    }

    def "should implement equals correctly"() {
        given:
        def uri1 = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123')
        def uri2 = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123')
        def uri3 = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid456')

        expect:
        uri1 == uri2
        uri1 != uri3
    }

    def "should implement hashCode correctly"() {
        given:
        def uri1 = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123')
        def uri2 = LaminUriParser.parse('lamin://laminlabs/lamindata/artifact/uid123')

        expect:
        uri1.hashCode() == uri2.hashCode()
    }

    // Error cases

    def "should throw on null URI string"() {
        when:
        LaminUriParser.parse((String) null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should throw on empty URI string"() {
        when:
        LaminUriParser.parse('')

        then:
        thrown(IllegalArgumentException)
    }

    def "should throw on null URI object"() {
        when:
        LaminUriParser.parse((URI) null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should throw on wrong scheme"() {
        when:
        LaminUriParser.parse('s3://bucket/key')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Invalid scheme")
    }

    def "should throw on missing components"() {
        when:
        LaminUriParser.parse('lamin://laminlabs')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Invalid URI format")
    }

    def "should throw when an artifact URI has no uid"() {
        when:
        LaminUriParser.parse('lamin://laminlabs/lamindata/artifact')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Invalid URI format")
    }

    def "should throw on unsupported resource type"() {
        when:
        LaminUriParser.parse('lamin://laminlabs/lamindata/collection/uid123')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Unsupported resource type")
    }

    @Unroll
    def "should parse various valid URIs: #uri"() {
        when:
        def parsed = LaminUriParser.parse(uri)

        then:
        parsed.owner == expectedOwner
        parsed.instance == expectedInstance
        parsed.resourceId == expectedId

        where:
        uri                                                    | expectedOwner | expectedInstance | expectedId
        'lamin://org/inst/artifact/abc123'                     | 'org'         | 'inst'           | 'abc123'
        'lamin://my-org/my-instance/artifact/uid_with_under'   | 'my-org'      | 'my-instance'    | 'uid_with_under'
        'lamin://o/i/artifact/u'                               | 'o'           | 'i'              | 'u'
    }

    // ==================== Storage URIs ====================

    def "artifact URIs are of kind ARTIFACT"() {
        expect:
        LaminUriParser.parse('lamin://org/inst/artifact/abc123').kind == LaminUriKind.ARTIFACT
        !LaminUriParser.parse('lamin://org/inst/artifact/abc123').storage
    }

    @Unroll
    def "should parse the three publish grammars: #uri"() {
        when:
        def parsed = LaminUriParser.parse(uri)

        then:
        parsed.kind == LaminUriKind.STORAGE
        parsed.storage
        parsed.spaceRef == expectedSpace
        parsed.storageReference == expectedStorage
        parsed.key == expectedKey

        where:
        uri                                                                 | expectedSpace  | expectedStorage | expectedKey
        'lamin://org/inst'                                                  | null           | null            | null
        'lamin://org/inst?prefix=results'                                   | null           | null            | 'results'
        'lamin://org/inst?space=spce12345678'                               | 'spce12345678' | null            | null
        'lamin://org/inst?storage=stor12345678'                             | null           | 'stor12345678'  | null
        'lamin://org/inst?space=spce12345678&storage=stor12345678&prefix=r' | 'spce12345678' | 'stor12345678'  | 'r'
        'lamin://org/inst/space/spce12345678'                               | 'spce12345678' | null            | null
        'lamin://org/inst/space/spce12345678?storage=stor12345678&prefix=q' | 'spce12345678' | 'stor12345678'  | 'q'
        'lamin://org/inst/storage/stor12345678'                             | null           | 'stor12345678'  | null
    }

    @Unroll
    def "should render publish URIs canonically: #uri -> #expected"() {
        expect:
        LaminUriParser.parse(uri).toUriString() == expected

        where:
        uri                                                         || expected
        'lamin://org/inst'                                          || 'lamin://org/inst'
        'lamin://org/inst?prefix=results'                           || 'lamin://org/inst?prefix=results'
        'lamin://org/inst?prefix=/results/'                         || 'lamin://org/inst?prefix=results'
        'lamin://org/inst?space=sp12345'                            || 'lamin://org/inst/space/sp12345'
        'lamin://org/inst?storage=st12345'                          || 'lamin://org/inst/storage/st12345'
        'lamin://org/inst/space/sp12345?storage=st1&prefix=out'     || 'lamin://org/inst/space/sp12345?storage=st1&prefix=out'
        'lamin://org/inst/storage/st1?prefix=out/qc/report.html'    || 'lamin://org/inst/storage/st1?prefix=out/qc/report.html'
        'lamin://org/inst/storage/st1'                              || 'lamin://org/inst/storage/st1'
    }

    @Unroll
    def "should reject a #type selector that is not a UID"() {
        when:
        LaminUriParser.parse("lamin://org/inst?${type}=s3://bucket/root")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("A ${type} must be selected by its UID")

        where:
        type << ['space', 'storage']
    }

    @Unroll
    def "parsing a canonical URI should round-trip: #uri"() {
        given:
        def parsed = LaminUriParser.parse(uri)

        expect:
        LaminUriParser.parse(parsed.toUriString()) == parsed
        LaminUriParser.parse(parsed.toUriString()).toUriString() == parsed.toUriString()

        where:
        uri << [
            'lamin://org/inst/artifact/abc123',
            'lamin://org/inst/artifact/abc123/sub/dir/file.txt',
            'lamin://org/inst',
            'lamin://org/inst?prefix=results',
            'lamin://org/inst?space=sp1&storage=st1&prefix=results/qc',
            'lamin://org/inst/space/sp1',
            'lamin://org/inst/storage/st1?prefix=a/b/c.txt',
            'lamin://org/inst?prefix=with space/and%23hash',
            'lamin://org/inst?prefix=artifact/space/storage'
        ]
    }

    @Unroll
    def "should normalise the key: #prefix -> #expected"() {
        expect:
        LaminUriParser.parse("lamin://org/inst?prefix=${prefix}").key == expected

        where:
        prefix              || expected
        'results'           || 'results'
        '/results'          || 'results'
        'results/'          || 'results'
        'a//b'              || 'a/b'
        'a/./b'             || 'a/b'
        'a/b/../c'          || 'a/c'
        '.'                 || null
        '/'                 || null
    }

    def "should reject a key that escapes the storage root"() {
        when:
        LaminUriParser.parse('lamin://org/inst?prefix=../elsewhere')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('outside of the storage root')
    }

    @Unroll
    def "should reject the reserved .lamindb prefix: #prefix"() {
        when:
        LaminUriParser.parse("lamin://org/inst?prefix=${prefix}")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('reserved by LaminDB')

        where:
        prefix << ['.lamindb', '.lamindb/results', '/.lamindb/']
    }

    def "should reject unknown query parameters"() {
        when:
        LaminUriParser.parse('lamin://org/inst?prefixx=results')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Unknown query parameter 'prefixx'")
    }

    def "should reject query parameters on artifact URIs"() {
        when:
        LaminUriParser.parse('lamin://org/inst/artifact/abc123?prefix=results')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('not supported for artifact URIs')
    }

    @Unroll
    def "should reject a duplicate #type selector"() {
        when:
        LaminUriParser.parse("lamin://org/inst/${type}/one?${type}=two")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Duplicate ${type} selector")

        where:
        type << ['space', 'storage']
    }

    def "should reject a key given as path segments"() {
        when:
        LaminUriParser.parse('lamin://org/inst/storage/abc/results/file.txt')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("must be given as '?prefix=...'")
    }

    def "should reject an empty selector"() {
        when:
        LaminUriParser.parse('lamin://org/inst/space/')

        then:
        thrown(IllegalArgumentException)
    }

    // ==================== Storage navigation ====================

    def "withSubPath should append to the key and normalise"() {
        given:
        def parsed = LaminUriParser.parse('lamin://org/inst?prefix=results')

        expect:
        parsed.withSubPath('qc/report.html').key == 'results/qc/report.html'
        parsed.withSubPath('./x').key == 'results/x'
    }

    def "withoutSubPath should return the storage root"() {
        given:
        def parsed = LaminUriParser.parse('lamin://org/inst/space/sp1?prefix=results/qc')

        when:
        def root = parsed.withoutSubPath()

        then:
        root.key == null
        root.spaceRef == 'sp1'
        root.toUriString() == 'lamin://org/inst/space/sp1'
    }

    def "getFileName and getParent should walk the key"() {
        given:
        def parsed = LaminUriParser.parse('lamin://org/inst?prefix=results/qc/report.html')

        expect:
        parsed.fileName == 'report.html'
        parsed.parent.key == 'results/qc'
        parsed.parent.parent.key == 'results'
        parsed.parent.parent.parent.key == null
        parsed.parent.parent.parent.parent == null
    }

    def "getFileName should be null for the storage root"() {
        expect:
        LaminUriParser.parse('lamin://org/inst').fileName == null
    }

    def "getKeySegments should split the key"() {
        expect:
        LaminUriParser.parse('lamin://org/inst?prefix=a/b/c').keySegments == ['a', 'b', 'c']
        LaminUriParser.parse('lamin://org/inst').keySegments == []
    }
}
