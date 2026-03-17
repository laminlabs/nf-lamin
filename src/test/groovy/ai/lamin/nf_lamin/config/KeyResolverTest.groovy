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
import java.net.URI
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
        KeyResolver.resolveKey('{basename}', Paths.get('/path/to/report.html')) == 'report.html'
    }

    def "resolveKey should replace {filename} variable"() {
        expect:
        KeyResolver.resolveKey('{filename}', Paths.get('/path/to/report.html')) == 'report'
    }

    def "resolveKey should replace {ext} variable"() {
        expect:
        KeyResolver.resolveKey('{ext}', Paths.get('/path/to/report.html')) == '.html'
    }

    def "resolveKey should replace {parent} variable"() {
        expect:
        KeyResolver.resolveKey('{parent}', Paths.get('/path/to/output/report.html')) == 'output'
    }

    def "resolveKey should replace {parent.parent} variable"() {
        expect:
        KeyResolver.resolveKey('{parent.parent}', Paths.get('/path/to/output/report.html')) == 'to'
    }

    def "resolveKey should replace {parent.parent.parent} variable"() {
        expect:
        KeyResolver.resolveKey('{parent.parent.parent}', Paths.get('/path/to/output/report.html')) == 'path'
    }

    def "resolveKey should replace multiple variables"() {
        expect:
        KeyResolver.resolveKey('prefix/{parent}/{basename}', Paths.get('/results/multiqc/report.html')) == 'prefix/multiqc/report.html'
    }

    def "resolveKey should return null for null template"() {
        expect:
        KeyResolver.resolveKey(null, Paths.get('/path/to/file.txt')) == null
    }

    def "resolveKey with compound extension"() {
        expect:
        KeyResolver.resolveKey('{filename}{ext}', Paths.get('/data/sample.fastq.gz')) == 'sample.fastq.gz'
    }

    def "resolveKey with nested parent variables"() {
        expect:
        KeyResolver.resolveKey('{parent.parent}/{parent}/{basename}', Paths.get('/home/user/results/multiqc/report.html')) == 'results/multiqc/report.html'
    }

    def "resolveKey with parent beyond depth returns empty"() {
        expect:
        KeyResolver.resolveKey('{parent.parent.parent.parent}/{basename}', Paths.get('/a/b/file.txt')) == '/file.txt'
    }

    // ========================= resolveKey with Closures =========================

    def "resolveKey should invoke closure with Path"() {
        given:
        Path path = Paths.get('/path/to/report.html')
        Closure key = { Path p -> p.fileName.toString() }

        expect:
        KeyResolver.resolveKey(key, path) == 'report.html'
    }

    def "resolveKey with closure that returns custom key"() {
        given:
        Path path = Paths.get('/data/sample.fastq.gz')
        Closure key = { Path p -> "custom-prefix/${p.fileName}" }

        expect:
        KeyResolver.resolveKey(key, path) == 'custom-prefix/sample.fastq.gz'
    }

    def "resolveKey with closure returning null falls back to basename"() {
        given:
        Path path = Paths.get('/path/to/report.html')
        Closure key = { Path p -> null }

        expect:
        KeyResolver.resolveKey(key, path) == 'report.html'
    }

    def "resolveKey with closure that throws falls back to basename"() {
        given:
        Path path = Paths.get('/path/to/report.html')
        Closure key = { Path p -> throw new RuntimeException("test error") }

        expect:
        KeyResolver.resolveKey(key, path) == 'report.html'
    }

    def "resolveKey closure receives actual Path object"() {
        given:
        Path path = Paths.get('/results/star/Aligned.bam')
        Closure key = { Path p -> "${p.parent.fileName}/${p.fileName}" }

        expect:
        KeyResolver.resolveKey(key, path) == 'star/Aligned.bam'
    }

    // ========================= Integration scenarios =========================

    def "default key from S3 path"() {
        expect:
        KeyResolver.defaultKeyFromPath('s3://lamindata/.lamindb/abc123.html') == 'abc123.html'
    }

    def "string template with S3 path"() {
        given:
        Path path = Mock(Path)
        path.toUri() >> URI.create('s3://bucket/results/star/Aligned.bam')

        expect:
        KeyResolver.resolveKey('pipeline-output/{parent}/{basename}', path) == 'pipeline-output/star/Aligned.bam'
    }

    def "parent variables with deep S3 path"() {
        given:
        Path path = Mock(Path)
        path.toUri() >> URI.create('s3://bucket/results/multiqc/star/multiqc_report.html')

        expect:
        KeyResolver.resolveKey('{parent.parent}/{parent}/{basename}', path) == 'multiqc/star/multiqc_report.html'
    }

    // ========================= resolveKey with Map config =========================
    //
    // In practice, pathStr = path.toUri().toString(), so:
    //   - local files  → "file:///abs/path/to/file.txt"  (path always non-null)
    //   - s3:// output → "s3://bucket/results/file.txt"  (path = S3Path, non-null)
    //   - gs:// output → "gs://bucket/results/file.txt"  (path = GcsPath, non-null)
    //   - https:// input → "https://host/file.txt"       (path = staged local Path)
    //
    // All schemes are handled uniformly via java.net.URI.relativize().
    // Cloud URI tests use mock Paths because S3Path/GcsPath can't be instantiated
    // in unit tests without cloud filesystem providers.

    // --- Local file:// paths ---

    @Unroll
    def "resolveKey [relativize] local: '#baseDir' + '#localPath' -> '#expected'"() {
        given:
        Path path = Paths.get(localPath).toAbsolutePath()

        expect:
        KeyResolver.resolveKey([relativize: baseDir], path) == expected

        where:
        baseDir                   | localPath                                    | expected
        '/output/quantms'         | '/output/quantms/msstats/results.csv'        | 'msstats/results.csv'
        '/output/quantms/'        | '/output/quantms/multiqc/report.html'        | 'multiqc/report.html'  // trailing slash
        '/output/quantms'         | '/output/quantms/a/b/c/deep.txt'             | 'a/b/c/deep.txt'       // deep nesting
        'output/quantms'          | 'output/quantms/multiqc/report.html'         | 'multiqc/report.html'  // relative base
    }

    // --- Cloud URI paths: s3://, gs://, az:// ---
    // Mock Paths because S3Path/GcsPath can't be instantiated without cloud providers.

    @Unroll
    def "resolveKey [relativize] cloud: '#baseDir' + '#uriStr' -> '#expected'"() {
        given:
        Path path = Mock(Path)
        path.toUri() >> URI.create(uriStr)

        expect:
        KeyResolver.resolveKey([relativize: baseDir], path) == expected

        where:
        baseDir                        | uriStr                                             | expected
        's3://bucket/results/run'      | 's3://bucket/results/run/multiqc/report.html'      | 'multiqc/report.html'
        's3://bucket/results/run/'     | 's3://bucket/results/run/a/b/file.txt'             | 'a/b/file.txt'         // trailing slash
        'gs://bucket/results/run'      | 'gs://bucket/results/run/multiqc/report.html'      | 'multiqc/report.html'
        'az://container/results/run'   | 'az://container/results/run/sub/file.parquet'      | 'sub/file.parquet'
    }

    // --- Mismatch: different bucket / host → falls back to basename ---

    @Unroll
    def "resolveKey [relativize] mismatch: '#baseDir' + '#uriStr' -> basename"() {
        given:
        Path path = Mock(Path)
        path.toUri() >> URI.create(uriStr)

        expect:
        KeyResolver.resolveKey([relativize: baseDir], path) == expected

        where:
        baseDir                        | uriStr                                             | expected
        's3://other-bucket/results'    | 's3://bucket/results/file.txt'                     | 'file.txt'
        'gs://bucket/results'          | 'gs://other-bucket/results/file.txt'               | 'file.txt'
        's3://bucket/results/run-a'    | 's3://bucket/results/run-b/file.txt'               | 'file.txt'
    }

    // --- Path not under base dir: URI.relativize leaves the URI absolute → basename fallback ---

    def "resolveKey [relativize] path outside base dir falls back to basename"() {
        given:
        Path path = Paths.get('/work/staging/abc123/BSA.fasta').toAbsolutePath()

        expect:
        // baseUri = file:///output/quantms/  — path is not under it
        // URI.relativize returns the original absolute URI → isAbsolute() → basename fallback
        KeyResolver.resolveKey([relativize: '/output/quantms'], path) == 'BSA.fasta'
    }

    // --- Edge cases ---

    def "resolveKey [relativize] with mock path resolves via URI relativize"() {
        given:
        Path path = Mock(Path)
        path.toUri() >> URI.create('file:///output/quantms/report.html')

        expect:
        KeyResolver.resolveKey([relativize: '/output/quantms'], path) == 'report.html'
    }

    def "resolveKey with Map config and unrecognized keys falls back to basename"() {
        given:
        Path path = Paths.get('/output/quantms/report.html')

        expect:
        KeyResolver.resolveKey([unknown: 'value'], path) == 'report.html'
    }
}
