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

import java.nio.file.attribute.FileTime
import java.time.Instant

import software.amazon.awssdk.services.s3.model.HeadObjectResponse

class LaminS3FileAttributesTest extends Specification {

    // ==================== lastModifiedTime ====================

    def "should return lastModifiedTime from response"() {
        given:
        def instant = Instant.parse('2025-01-15T10:00:00Z')
        def response = HeadObjectResponse.builder()
            .lastModified(instant)
            .contentLength(1024L)
            .eTag('"abc123"')
            .build()
        def attrs = new LaminS3FileAttributes(response)

        expect:
        attrs.lastModifiedTime() == FileTime.from(instant)
    }

    def "should return epoch FileTime when lastModified is null"() {
        given:
        def response = HeadObjectResponse.builder()
            .contentLength(512L)
            .build()
        def attrs = new LaminS3FileAttributes(response)

        expect:
        attrs.lastModifiedTime() == FileTime.fromMillis(0)
    }

    def "lastAccessTime() should delegate to lastModifiedTime()"() {
        given:
        def instant = Instant.parse('2025-06-01T12:00:00Z')
        def response = HeadObjectResponse.builder().lastModified(instant).build()
        def attrs = new LaminS3FileAttributes(response)

        expect:
        attrs.lastAccessTime() == attrs.lastModifiedTime()
    }

    def "creationTime() should delegate to lastModifiedTime()"() {
        given:
        def instant = Instant.parse('2025-06-01T12:00:00Z')
        def response = HeadObjectResponse.builder().lastModified(instant).build()
        def attrs = new LaminS3FileAttributes(response)

        expect:
        attrs.creationTime() == attrs.lastModifiedTime()
    }

    // ==================== size ====================

    def "should return contentLength as size"() {
        given:
        def response = HeadObjectResponse.builder().contentLength(2048L).build()
        def attrs = new LaminS3FileAttributes(response)

        expect:
        attrs.size() == 2048L
    }

    def "should return 0 when contentLength is null"() {
        given:
        def response = HeadObjectResponse.builder().build()
        def attrs = new LaminS3FileAttributes(response)

        expect:
        attrs.size() == 0L
    }

    // ==================== fileKey ====================

    def "should return eTag as fileKey"() {
        given:
        def response = HeadObjectResponse.builder().eTag('"abc123def456"').build()
        def attrs = new LaminS3FileAttributes(response)

        expect:
        attrs.fileKey() == '"abc123def456"'
    }

    def "should return null fileKey when eTag is absent"() {
        given:
        def response = HeadObjectResponse.builder().build()
        def attrs = new LaminS3FileAttributes(response)

        expect:
        attrs.fileKey() == null
    }

    // ==================== type flags ====================

    def "should be a regular file"() {
        given:
        def response = HeadObjectResponse.builder().build()
        def attrs = new LaminS3FileAttributes(response)

        expect:
        attrs.isRegularFile()
        !attrs.isDirectory()
        !attrs.isSymbolicLink()
        !attrs.isOther()
    }
}
