package ai.lamin.nf_lamin.util

import ai.lamin.nf_lamin.util.TransformInfoHelper

import spock.lang.Specification
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class TransformInfoHelperTest extends Specification {

    // ========== generateTransformSourceCode tests ==========
    // These verify the source_code format matches lamindb's Transform.from_git()
    // See: https://github.com/laminlabs/lamindb/blob/main/lamindb/models/transform.py
    //
    // Key insight from Python implementation:
    // - Pinned transform: TAG or COMMIT → uses "commit:" (immutable code state)
    // - Sliding transform: BRANCH → uses "branch:" (code can change)
    //
    // The revisionType is detected by inspecting the .git directory in the
    // projectDir, since Nextflow's WorkflowMetadata doesn't expose the
    // revision type (only commitId and revision name).

    def 'generateTransformSourceCode with TAG uses commit for pinned transform'() {
        given: 'metadata with TAG revision type (pinned/immutable transform)'
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: 'https://github.com/laminlabs/nf-lamin',
            mainScript: 'main.nf',
            revision: 'v1.0.0',
            commitId: 'abc123def456789012345678901234567890abcd',
            entrypoint: null,
            projectDir: Paths.get('/opt/workflows/test'),
            manifestName: 'test',
            manifestDescription: 'test desc',
            revisionType: TransformInfoHelper.RevisionType.TAG
        )

        when:
        String sourceCode = TransformInfoHelper.generateTransformSourceCode(metadata)

        then: 'format matches lamindb: repo:\npath:\ncommit:'
        sourceCode == '''\
repo: https://github.com/laminlabs/nf-lamin
path: main.nf
commit: abc123def456789012345678901234567890abcd'''
    }

    def 'generateTransformSourceCode with BRANCH uses branch for sliding transform'() {
        given: 'metadata with BRANCH revision type (sliding/mutable transform)'
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: 'https://github.com/laminlabs/nf-lamin',
            mainScript: 'workflows/analysis.nf',
            revision: 'main',
            commitId: 'abc123def456789012345678901234567890abcd',
            entrypoint: null,
            projectDir: Paths.get('/opt/workflows/test'),
            manifestName: 'test',
            manifestDescription: 'test desc',
            revisionType: TransformInfoHelper.RevisionType.BRANCH
        )

        when:
        String sourceCode = TransformInfoHelper.generateTransformSourceCode(metadata)

        then: 'format matches lamindb: repo:\npath:\nbranch:'
        sourceCode == '''\
repo: https://github.com/laminlabs/nf-lamin
path: workflows/analysis.nf
branch: main'''
    }

    def 'generateTransformSourceCode with COMMIT uses commit for pinned transform'() {
        given: 'metadata with COMMIT revision type (detached HEAD state)'
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: 'https://github.com/laminlabs/nf-lamin',
            mainScript: 'main.nf',
            revision: 'abc123d',
            commitId: 'abc123def456789012345678901234567890abcd',
            entrypoint: null,
            projectDir: Paths.get('/opt/workflows/test'),
            manifestName: 'test',
            manifestDescription: 'test desc',
            revisionType: TransformInfoHelper.RevisionType.COMMIT
        )

        when:
        String sourceCode = TransformInfoHelper.generateTransformSourceCode(metadata)

        then: 'uses commit hash for pinned transform'
        sourceCode == '''\
repo: https://github.com/laminlabs/nf-lamin
path: main.nf
commit: abc123def456789012345678901234567890abcd'''
    }

    def 'generateTransformSourceCode without revisionType and without commitId uses branch'() {
        given: 'metadata without git info (local development without git tracking)'
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: '/local/path/to/workflow',
            mainScript: 'main.nf',
            revision: 'local-development',
            commitId: null,
            entrypoint: null,
            projectDir: Paths.get('/local/path/to/workflow'),
            manifestName: 'test',
            manifestDescription: 'test desc',
            revisionType: null
        )

        when:
        String sourceCode = TransformInfoHelper.generateTransformSourceCode(metadata)

        then: 'uses branch for local development'
        sourceCode == '''\
repo: /local/path/to/workflow
path: main.nf
branch: local-development'''
    }

    def 'generateTransformSourceCode without revisionType but with commitId uses commit'() {
        given: 'metadata with commitId but unknown revision type (fallback to pinned)'
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: 'https://github.com/test/repo',
            mainScript: 'main.nf',
            revision: 'unknown',
            commitId: 'abc123def456',
            entrypoint: null,
            projectDir: Paths.get('/tmp'),
            manifestName: 'test',
            manifestDescription: 'test desc',
            revisionType: null
        )

        when:
        String sourceCode = TransformInfoHelper.generateTransformSourceCode(metadata)

        then: 'defaults to commit for safety (immutable is safer assumption)'
        sourceCode.contains('commit: abc123def456')
        !sourceCode.contains('branch:')
    }

    def 'generateTransformSourceCode with entrypoint includes entrypoint field'() {
        given: 'metadata with an entrypoint specified'
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: 'https://github.com/nf-core/scrnaseq',
            mainScript: 'main.nf',
            revision: 'main',
            commitId: 'def456abc789012345678901234567890abcdef',
            entrypoint: 'CELLRANGER',
            projectDir: Paths.get('/opt/workflows/test'),
            manifestName: 'nf-core/scrnaseq',
            manifestDescription: 'Single-cell RNA-seq',
            revisionType: TransformInfoHelper.RevisionType.TAG
        )

        when:
        String sourceCode = TransformInfoHelper.generateTransformSourceCode(metadata)

        then: 'format includes entrypoint between path and commit'
        sourceCode == '''\
repo: https://github.com/nf-core/scrnaseq
path: main.nf
entrypoint: CELLRANGER
commit: def456abc789012345678901234567890abcdef'''
    }

    def 'generateTransformSourceCode field order is preserved'() {
        given: 'metadata with all fields'
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: 'https://github.com/test/repo',
            mainScript: 'workflow.nf',
            revision: 'develop',
            commitId: '1234567890abcdef1234567890abcdef12345678',
            entrypoint: 'MY_ENTRY',
            projectDir: Paths.get('/tmp'),
            manifestName: 'test',
            manifestDescription: 'test',
            revisionType: TransformInfoHelper.RevisionType.TAG
        )

        when:
        String sourceCode = TransformInfoHelper.generateTransformSourceCode(metadata)
        List<String> lines = sourceCode.split('\n') as List

        then: 'fields appear in correct order: repo, path, entrypoint, commit/branch'
        lines[0].startsWith('repo:')
        lines[1].startsWith('path:')
        lines[2].startsWith('entrypoint:')
        lines[3].startsWith('commit:')
    }

    def 'generateTransformSourceCode without entrypoint omits entrypoint field'() {
        given: 'metadata without entrypoint'
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: 'https://github.com/test/repo',
            mainScript: 'main.nf',
            revision: 'main',
            commitId: 'abc123',
            entrypoint: null,
            projectDir: Paths.get('/tmp'),
            manifestName: 'test',
            manifestDescription: 'test',
            revisionType: TransformInfoHelper.RevisionType.TAG
        )

        when:
        String sourceCode = TransformInfoHelper.generateTransformSourceCode(metadata)

        then:
        !sourceCode.contains('entrypoint:')
        sourceCode.split('\n').size() == 3  // repo, path, commit
    }

    def 'generateTransformSourceCode uses branch when running from branch even with commitId'() {
        given: '''metadata running from a branch (e.g., -r main) with revisionType BRANCH.
                  Even though we have a commitId, branches are sliding transforms.'''
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: 'https://github.com/test/repo',
            mainScript: 'main.nf',
            revision: 'main',
            commitId: 'abc123def456',  // Has commitId from remote checkout
            entrypoint: null,
            projectDir: Paths.get('/tmp'),
            manifestName: 'test',
            manifestDescription: 'test',
            revisionType: TransformInfoHelper.RevisionType.BRANCH
        )

        when:
        String sourceCode = TransformInfoHelper.generateTransformSourceCode(metadata)

        then: 'uses branch because revisionType is BRANCH (sliding transform)'
        sourceCode.contains('branch: main')
        !sourceCode.contains('commit:')
    }

    def 'generateTransformSourceCode uses commit when running from tag'() {
        given: 'metadata running from a tag (e.g., -r v1.0.0)'
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: 'https://github.com/test/repo',
            mainScript: 'main.nf',
            revision: 'v1.0.0',
            commitId: 'abc123def456',
            entrypoint: null,
            projectDir: Paths.get('/tmp'),
            manifestName: 'test',
            manifestDescription: 'test',
            revisionType: TransformInfoHelper.RevisionType.TAG
        )

        when:
        String sourceCode = TransformInfoHelper.generateTransformSourceCode(metadata)

        then: 'uses commit because revisionType is TAG (pinned transform)'
        sourceCode.contains('commit: abc123def456')
        !sourceCode.contains('branch:')
    }

    def 'generateTransformSourceCode handles nested script paths'() {
        given: 'metadata with nested script path'
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: 'https://github.com/org/monorepo',
            mainScript: 'pipelines/rnaseq/main.nf',
            revision: 'main',
            commitId: null,
            entrypoint: null,
            projectDir: Paths.get('/tmp'),
            manifestName: 'test',
            manifestDescription: 'test',
            revisionType: null
        )

        when:
        String sourceCode = TransformInfoHelper.generateTransformSourceCode(metadata)

        then:
        sourceCode.contains('path: pipelines/rnaseq/main.nf')
    }

    def 'generateTransformSourceCode handles different git providers'() {
        given: 'metadata with GitLab repository'
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: 'https://gitlab.com/myorg/myrepo',
            mainScript: 'main.nf',
            revision: 'main',
            commitId: 'abc123',
            entrypoint: null,
            projectDir: Paths.get('/tmp'),
            manifestName: 'test',
            manifestDescription: 'test',
            revisionType: TransformInfoHelper.RevisionType.TAG
        )

        when:
        String sourceCode = TransformInfoHelper.generateTransformSourceCode(metadata)

        then:
        sourceCode.startsWith('repo: https://gitlab.com/myorg/myrepo')
    }

    // ========== generateTransformKey tests ==========

    def 'generateTransformKey returns repository for main.nf'() {
        given:
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: 'https://github.com/test/repo',
            mainScript: 'main.nf'
        )

        when:
        String key = TransformInfoHelper.generateTransformKey(metadata)

        then:
        key == 'https://github.com/test/repo'
    }

    def 'generateTransformKey returns repository:script for non-main scripts'() {
        given:
        def metadata = new TransformInfoHelper.TransformMetadata(
            repository: 'https://github.com/test/repo',
            mainScript: 'workflows/analysis.nf'
        )

        when:
        String key = TransformInfoHelper.generateTransformKey(metadata)

        then:
        key == 'https://github.com/test/repo:workflows/analysis.nf'
    }

    // ========== generateTransformDescription tests ==========

    def 'generateTransformDescription combines manifest name and description'() {
        given:
        def metadata = new TransformInfoHelper.TransformMetadata(
            manifestName: 'nf-core/rnaseq',
            manifestDescription: 'RNA sequencing analysis pipeline'
        )

        when:
        String description = TransformInfoHelper.generateTransformDescription(metadata)

        then:
        description == 'nf-core/rnaseq: RNA sequencing analysis pipeline'
    }

    // ========== detectSchemaPath tests ==========

    def 'detectSchemaPath finds the schema next to a nested main script'() {
        given:
        Path projectDir = Files.createTempDirectory('nf-lamin-schema')
        Path scriptDir = Files.createDirectories(projectDir.resolve('target/nextflow/workflows/qc'))
        Path scriptFile = Files.createFile(scriptDir.resolve('main.nf'))
        Files.writeString(scriptDir.resolve('nextflow_schema.json'), '{}')
        Files.writeString(projectDir.resolve('nextflow_schema.json'), '{}')

        expect:
        TransformInfoHelper.detectSchemaPath(projectDir, scriptFile) == 'target/nextflow/workflows/qc/nextflow_schema.json'
    }

    def 'detectSchemaPath finds the schema next to a root main script'() {
        given:
        Path projectDir = Files.createTempDirectory('nf-lamin-schema')
        Path scriptFile = Files.createFile(projectDir.resolve('main.nf'))
        Files.writeString(projectDir.resolve('nextflow_schema.json'), '{}')

        expect:
        TransformInfoHelper.detectSchemaPath(projectDir, scriptFile) == 'nextflow_schema.json'
    }

    def 'detectSchemaPath returns null without a schema next to the main script'() {
        given:
        Path projectDir = Files.createTempDirectory('nf-lamin-schema')
        Path scriptDir = Files.createDirectories(projectDir.resolve('workflows/qc'))
        Path scriptFile = Files.createFile(scriptDir.resolve('main.nf'))
        Files.writeString(projectDir.resolve('nextflow_schema.json'), '{}')

        expect:
        TransformInfoHelper.detectSchemaPath(projectDir, scriptFile) == null
    }

    def 'detectSchemaPath returns null for a script outside the project dir'() {
        given:
        Path projectDir = Files.createTempDirectory('nf-lamin-schema')
        Path otherDir = Files.createTempDirectory('nf-lamin-other')
        Path scriptFile = Files.createFile(otherDir.resolve('main.nf'))
        Files.writeString(otherDir.resolve('nextflow_schema.json'), '{}')

        expect:
        TransformInfoHelper.detectSchemaPath(projectDir, scriptFile) == null
    }

    def 'detectSchemaPath returns null without a project dir or script'() {
        expect:
        TransformInfoHelper.detectSchemaPath(null, Paths.get('/tmp/main.nf')) == null
        TransformInfoHelper.detectSchemaPath(Paths.get('/tmp'), null) == null
    }

    // ========== withSchemaName tests ==========

    def 'withSchemaName stores the schema path as nextflow launch setting'() {
        expect:
        TransformInfoHelper.withSchemaName(null, 'target/nextflow/qc/nextflow_schema.json') ==
            [nextflow: [launch: [schemaName: 'target/nextflow/qc/nextflow_schema.json']]]
    }

    def 'withSchemaName keeps the other aux settings'() {
        given:
        Map existing = [
            ei: 'x',
            nextflow: [
                launch: [configProfiles: ['docker']],
                parameter_config: [input: [schema_uid: 'abc']]
            ]
        ]

        when:
        Map aux = TransformInfoHelper.withSchemaName(existing, 'nextflow_schema.json')

        then:
        aux == [
            ei: 'x',
            nextflow: [
                launch: [configProfiles: ['docker'], schemaName: 'nextflow_schema.json'],
                parameter_config: [input: [schema_uid: 'abc']]
            ]
        ]
        existing.nextflow == [launch: [configProfiles: ['docker']], parameter_config: [input: [schema_uid: 'abc']]]
    }

    def 'withSchemaName leaves an existing schema path alone'() {
        expect:
        TransformInfoHelper.withSchemaName([nextflow: [launch: [schemaName: 'custom/schema.json']]], 'nextflow_schema.json') == null
    }

    def 'withSchemaName returns null without a schema path'() {
        expect:
        TransformInfoHelper.withSchemaName([ei: 'x'], null) == null
    }
}
