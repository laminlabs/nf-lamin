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

package ai.lamin.nf_lamin.config

import spock.lang.Specification
import spock.lang.Unroll
import java.nio.file.Path
import java.nio.file.Paths

class KeyResolverTest extends Specification {

    // ========================= extractBasename =========================

    @Unroll
    def "extractBasename from '#path' should return '#expected'"() {
        expect:
        KeyResolver.extractBasename(path) == expected

        where:
        path                                            | expected
        '/home/user/output/report.html'                 | 'report.html'
        's3://bucket/results/multiqc/report.html'       | 'report.html'
        'file.txt'                                      | 'file.txt'
        '/file.txt'                                     | 'file.txt'
        '/path/to/file.fastq.gz'                        | 'file.fastq.gz'
        ''                                              | ''
        null                                            | ''
        '/path/to/dir/'                                 | 'dir'
    }

    // ========================= extractFilename =========================

    @Unroll
    def "extractFilename from '#basename' should return '#expected'"() {
        expect:
        KeyResolver.extractFilename(basename) == expected

        where:
        basename            | expected
        'report.html'       | 'report'
        'file.fastq.gz'     | 'file'
        'noext'             | 'noext'
        '.hidden'           | '.hidden'
        ''                  | ''
        null                | ''
    }

    // ========================= extractExtension =========================

    @Unroll
    def "extractExtension from '#basename' should return '#expected'"() {
        expect:
        KeyResolver.extractExtension(basename) == expected

        where:
        basename            | expected
        'report.html'       | '.html'
        'file.fastq.gz'     | '.fastq.gz'
        'noext'             | ''
        '.hidden'           | ''
        ''                  | ''
        null                | ''
    }

    // ========================= extractParentAtLevel =========================

    @Unroll
    def "extractParentAtLevel('#path', #level) should return '#expected'"() {
        expect:
        KeyResolver.extractParentAtLevel(path, level) == expected

        where:
        path                                            | level | expected
        '/home/user/output/report.html'                 | 1     | 'output'
        '/home/user/output/report.html'                 | 2     | 'user'
        '/home/user/output/report.html'                 | 3     | 'home'
        '/home/user/output/report.html'                 | 4     | ''
        's3://bucket/results/multiqc/report.html'       | 1     | 'multiqc'
        's3://bucket/results/multiqc/report.html'       | 2     | 'results'
        's3://bucket/results/multiqc/report.html'       | 3     | 'bucket'
        'file.txt'                                      | 1     | ''
        '/file.txt'                                     | 1     | ''
        '/a/b/c/d.txt'                                  | 1     | 'c'
        '/a/b/c/d.txt'                                  | 2     | 'b'
        '/a/b/c/d.txt'                                  | 3     | 'a'
        ''                                              | 1     | ''
        null                                            | 1     | ''
    }

    // ========================= defaultKeyFromPath =========================

    def "defaultKeyFromPath should return the basename"() {
        expect:
        KeyResolver.defaultKeyFromPath('/path/to/report.html') == 'report.html'
    }

    def "defaultKeyFromPath should return null for null path"() {
        expect:
        KeyResolver.defaultKeyFromPath(null) == null
    }

    def "defaultKeyFromPath should return null for empty path"() {
        expect:
        KeyResolver.defaultKeyFromPath('') == null
    }

    // ========================= resolveKey with String templates =========================

    def "resolveKey should replace {basename} variable"() {
        expect:
        KeyResolver.resolveKey('{basename}', '/path/to/report.html', null) == 'report.html'
    }

    def "resolveKey should replace {filename} variable"() {
        expect:
        KeyResolver.resolveKey('{filename}', '/path/to/report.html', null) == 'report'
    }

    def "resolveKey should replace {ext} variable"() {
        expect:
        KeyResolver.resolveKey('{ext}', '/path/to/report.html', null) == '.html'
    }

    def "resolveKey should replace {parent} variable"() {
        expect:
        KeyResolver.resolveKey('{parent}', '/path/to/output/report.html', null) == 'output'
    }

    def "resolveKey should replace {parent.parent} variable"() {
        expect:
        KeyResolver.resolveKey('{parent.parent}', '/path/to/output/report.html', null) == 'to'
    }

    def "resolveKey should replace {parent.parent.parent} variable"() {
        expect:
        KeyResolver.resolveKey('{parent.parent.parent}', '/path/to/output/report.html', null) == 'path'
    }

    def "resolveKey should replace multiple variables"() {
        expect:
        KeyResolver.resolveKey('prefix/{parent}/{basename}', '/results/multiqc/report.html', null) == 'prefix/multiqc/report.html'
    }

    def "resolveKey should return null for null template"() {
        expect:
        KeyResolver.resolveKey(null, '/path/to/file.txt', null) == null
    }

    def "resolveKey with compound extension"() {
        expect:
        KeyResolver.resolveKey('{filename}{ext}', '/data/sample.fastq.gz', null) == 'sample.fastq.gz'
    }

    def "resolveKey with nested parent variables"() {
        expect:
        KeyResolver.resolveKey('{parent.parent}/{parent}/{basename}', '/home/user/results/multiqc/report.html', null) == 'results/multiqc/report.html'
    }

    def "resolveKey with parent beyond depth returns empty"() {
        expect:
        KeyResolver.resolveKey('{parent.parent.parent.parent}/{basename}', '/a/b/file.txt', null) == '/file.txt'
    }

    // ========================= resolveKey with Closures =========================

    def "resolveKey should invoke closure with Path"() {
        given:
        Path path = Paths.get('/path/to/report.html')
        Closure key = { Path p -> p.fileName.toString() }

        expect:
        KeyResolver.resolveKey(key, path.toUri().toString(), path) == 'report.html'
    }

    def "resolveKey with closure that returns custom key"() {
        given:
        Path path = Paths.get('/data/sample.fastq.gz')
        Closure key = { Path p -> "custom-prefix/${p.fileName}" }

        expect:
        KeyResolver.resolveKey(key, path.toUri().toString(), path) == 'custom-prefix/sample.fastq.gz'
    }

    def "resolveKey with closure returning null falls back to basename"() {
        given:
        Path path = Paths.get('/path/to/report.html')
        Closure key = { Path p -> null }

        expect:
        KeyResolver.resolveKey(key, path.toUri().toString(), path) == 'report.html'
    }

    def "resolveKey with closure that throws falls back to basename"() {
        given:
        Path path = Paths.get('/path/to/report.html')
        Closure key = { Path p -> throw new RuntimeException("test error") }

        expect:
        KeyResolver.resolveKey(key, path.toUri().toString(), path) == 'report.html'
    }

    def "resolveKey closure receives actual Path object"() {
        given:
        Path path = Paths.get('/results/star/Aligned.bam')
        Closure key = { Path p -> "${p.parent.fileName}/${p.fileName}" }

        expect:
        KeyResolver.resolveKey(key, path.toUri().toString(), path) == 'star/Aligned.bam'
    }

    // ========================= Integration scenarios =========================

    def "default key from S3 path"() {
        expect:
        KeyResolver.defaultKeyFromPath('s3://lamindata/.lamindb/abc123.html') == 'abc123.html'
    }

    def "string template with S3 path"() {
        expect:
        KeyResolver.resolveKey('pipeline-output/{parent}/{basename}', 's3://bucket/results/star/Aligned.bam', null) == 'pipeline-output/star/Aligned.bam'
    }

    def "parent variables with deep S3 path"() {
        expect:
        KeyResolver.resolveKey('{parent.parent}/{parent}/{basename}', 's3://bucket/results/multiqc/star/multiqc_report.html', null) == 'multiqc/star/multiqc_report.html'
    }
}
