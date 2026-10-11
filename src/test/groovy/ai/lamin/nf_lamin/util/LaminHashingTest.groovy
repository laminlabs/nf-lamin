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

package ai.lamin.nf_lamin.util

import java.nio.file.Files
import java.nio.file.Path

import ai.lamin.nf_lamin.helpers.TestHelper
import ai.lamin.nf_lamin.model.ArtifactStats
import spock.lang.Specification

/**
 * Expected values come from lamindb-setup's tests/storage/test_hashing.py and were
 * re-derived against the installed lamindb_setup package.
 */
class LaminHashingTest extends Specification {

    static final String HEX_MD5 = '9b89c8c1acf79dba5b5341d1fff9806f'
    static final String B64_MD5 = 'm4nIwaz3nbpbU0HR__mAbw'

    // ========== primitives ==========

    def 'b16ToB64 converts a hex md5 to the url-safe base64 lamindb stores'() {
        expect:
        LaminHashing.b16ToB64(HEX_MD5) == B64_MD5
        LaminHashing.b16ToB64(HEX_MD5.toUpperCase()) == B64_MD5
        LaminHashing.b16ToB64('"' + HEX_MD5 + '"') == B64_MD5
    }

    def 'hashString hashes the utf-8 bytes with md5'() {
        expect:
        LaminHashing.hashString('a') == 'DMF1ucDxtqgxw5niaXcmYQ'
        LaminHashing.hashString('b') == 'kutf_uauL-w61xx3dTFXjw'
    }

    def 'hashFromHashes sorts the hashes before combining them'() {
        given:
        def hashes = [LaminHashing.hashString('a'), LaminHashing.hashString('b')]

        expect:
        LaminHashing.hashFromHashes(hashes) == 'YNCwyFw7rBegqZLBVEckCg'
        LaminHashing.hashFromHashes(hashes.reverse()) == 'YNCwyFw7rBegqZLBVEckCg'
    }

    // ========== files ==========

    def 'hashFile uses md5 for a file that fits in one chunk'() {
        given:
        Path file = TestHelper.createInMemTempFile('a.txt', content)

        when:
        ArtifactStats stats = LaminHashing.hashFile(file)

        then:
        stats.size == content.length()
        stats.hash == hash
        stats.hashType == 'md5'
        stats.nFiles == null

        where:
        content | hash
        'a'     | 'DMF1ucDxtqgxw5niaXcmYQ'
        'b'     | 'kutf_uauL-w61xx3dTFXjw'
        'abc'   | 'kAFQmDzST7DWlj99KOF_cg'
    }

    def 'hashFile uses sha1 of the first and last chunk for a file larger than one chunk'() {
        given:
        Path file = TestHelper.createInMemTempFile('abc.txt', 'abc')

        when:
        ArtifactStats stats = LaminHashing.hashFile(file, 1)

        then:
        stats.size == 3
        stats.hash == 'p0EbDbQEP1wS-Tw6TuBjKS'
        stats.hashType == 'sha1-fl'
    }

    def 'hashFile switches strategy right above the chunk size'() {
        given:
        Path exact = TestHelper.createInMemTempFile('exact.txt', 'abc')
        Path above = TestHelper.createInMemTempFile('above.txt', 'abcd')

        expect:
        LaminHashing.hashFile(exact, 3).hashType == 'md5'
        LaminHashing.hashFile(exact, 3).hash == 'kAFQmDzST7DWlj99KOF_cg'
        LaminHashing.hashFile(above, 3).hashType == 'sha1-fl'
    }

    // ========== directories ==========

    def 'hashDir combines the per-file hashes into md5-d'() {
        given:
        Path dir = TestHelper.createInMemTempFile('a', 'a').parent
        dir.resolve('b').text = 'b'

        when:
        ArtifactStats stats = LaminHashing.hashDir(dir)

        then:
        stats.size == 2
        stats.hash == 'YNCwyFw7rBegqZLBVEckCg'
        stats.hashType == 'md5-d'
        stats.nFiles == 2
    }

    def 'hashDir walks nested directories and ignores file names'() {
        given:
        Path dir = TestHelper.createInMemTempFile('x/a', null).parent.parent
        Files.createDirectories(dir.resolve('x'))
        dir.resolve('x/a').text = 'a'
        Files.createDirectories(dir.resolve('y/z'))
        dir.resolve('y/z/other-name').text = 'b'

        expect:
        LaminHashing.hashDir(dir).hash == 'YNCwyFw7rBegqZLBVEckCg'
        LaminHashing.hashDir(dir).nFiles == 2
    }

    def 'hashDir of an empty directory has no hash'() {
        given:
        Path dir = Files.createDirectories(TestHelper.createInMemTempFile('unused', null).parent.resolve('empty'))

        when:
        ArtifactStats stats = LaminHashing.hashDir(dir)

        then:
        stats.size == 0
        stats.hash == null
        stats.hashType == null
        stats.nFiles == 0
    }

    // ========== S3 etags ==========

    def 'fromEtag treats a plain etag as the md5 of the object'() {
        when:
        ArtifactStats stats = LaminHashing.fromEtag(42L, '"' + HEX_MD5 + '"')

        then:
        stats.size == 42
        stats.hash == B64_MD5
        stats.hashType == 'md5'
        stats.nFiles == null
    }

    def 'fromEtag keeps the part count of a multipart etag in the hash type'() {
        when:
        ArtifactStats stats = LaminHashing.fromEtag(42L, '"' + HEX_MD5 + '-5"')

        then:
        stats.hash == B64_MD5
        stats.hashType == 'md5-5'
    }

    def 'fromEtag without an etag yields no hash'() {
        expect:
        LaminHashing.fromEtag(42L, etag).hash == null
        LaminHashing.fromEtag(42L, etag).hashType == null
        LaminHashing.fromEtag(42L, etag).size == 42

        where:
        etag << [null, '', '""']
    }

    def 'fromEtags combines per-object hashes like a local directory'() {
        given:
        // the same md5s a local dir with files "a" and "b" would have, one of them "multipart"
        String etagA = '"' + hexOf('a') + '"'
        String etagB = '"' + hexOf('b') + '-3"'

        when:
        ArtifactStats stats = LaminHashing.fromEtags(2L, [etagA, etagB])

        then:
        stats.hash == 'YNCwyFw7rBegqZLBVEckCg'
        stats.hashType == 'md5-d'
        stats.size == 2
        stats.nFiles == 2
    }

    private static String hexOf(String content) {
        return java.security.MessageDigest.getInstance('MD5').digest(content.bytes).encodeHex().toString()
    }
}
