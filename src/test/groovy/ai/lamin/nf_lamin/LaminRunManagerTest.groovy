package ai.lamin.nf_lamin

import spock.lang.Specification
import java.nio.file.Paths
import ai.lamin.nf_lamin.util.GitHelper

class LaminRunManagerTest extends Specification {

    def setup() {
        LaminRunManager.instance.reset()
    }

    def 'stores and exposes transform and run metadata'() {
        given:
        Map<String, Object> transform = [uid: 'T123', id: 42]
        Map<String, Object> run = [uid: 'R456', id: 99]

        when:
        LaminRunManager.instance.updateTransform(transform)
        LaminRunManager.instance.updateRun(run)

        then:
        LaminRunManager.instance.transform.uid == 'T123'
        LaminRunManager.instance.run.uid == 'R456'

        and:
        def extension = new LaminExtension()
        extension.getTransformUid() == 'T123'
        extension.getRunUid() == 'R456'
    }

    def 'reset clears stored state'() {
        given:
        LaminRunManager.instance.updateRun([uid: 'R999'])

        when:
        LaminRunManager.instance.reset()

        then:
        LaminRunManager.instance.run == null
        new LaminExtension().getRunUid() == null
    }

    // ========== generateTransformSourceCode tests ==========
    // These verify the source_code format matches lamindb's Transform.from_git()

    def 'generateTransformSourceCode with commit for pinned transform matches lamindb format'() {
        given: 'metadata with a commit ID (pinned/immutable transform)'
        def metadata = new LaminRunManager.TransformMetadata(
            repository: 'https://github.com/laminlabs/nf-lamin',
            mainScript: 'main.nf',
            revision: 'v1.0.0',
            commitId: 'abc123def456789012345678901234567890abcd',
            entrypoint: null,
            projectDir: Paths.get('/opt/workflows/test'),
            manifestName: 'test',
            manifestDescription: 'test desc',
            gitInfo: null
        )

        when:
        String sourceCode = LaminRunManager.generateTransformSourceCode(metadata)

        then: 'format matches lamindb: repo:\npath:\ncommit:'
        sourceCode == '''\
repo: https://github.com/laminlabs/nf-lamin
path: main.nf
commit: abc123def456789012345678901234567890abcd'''
    }

    def 'generateTransformSourceCode with branch for sliding transform matches lamindb format'() {
        given: 'metadata without commit ID (sliding/mutable transform)'
        def metadata = new LaminRunManager.TransformMetadata(
            repository: 'https://github.com/laminlabs/nf-lamin',
            mainScript: 'workflows/analysis.nf',
            revision: 'main',
            commitId: null,
            entrypoint: null,
            projectDir: Paths.get('/opt/workflows/test'),
            manifestName: 'test',
            manifestDescription: 'test desc',
            gitInfo: null
        )

        when:
        String sourceCode = LaminRunManager.generateTransformSourceCode(metadata)

        then: 'format matches lamindb: repo:\npath:\nbranch:'
        sourceCode == '''\
repo: https://github.com/laminlabs/nf-lamin
path: workflows/analysis.nf
branch: main'''
    }

    def 'generateTransformSourceCode with entrypoint includes entrypoint field'() {
        given: 'metadata with an entrypoint specified'
        def metadata = new LaminRunManager.TransformMetadata(
            repository: 'https://github.com/nf-core/scrnaseq',
            mainScript: 'main.nf',
            revision: 'main',
            commitId: 'def456abc789012345678901234567890abcdef',
            entrypoint: 'CELLRANGER',
            projectDir: Paths.get('/opt/workflows/test'),
            manifestName: 'nf-core/scrnaseq',
            manifestDescription: 'Single-cell RNA-seq',
            gitInfo: null
        )

        when:
        String sourceCode = LaminRunManager.generateTransformSourceCode(metadata)

        then: 'format includes entrypoint between path and commit'
        sourceCode == '''\
repo: https://github.com/nf-core/scrnaseq
path: main.nf
entrypoint: CELLRANGER
commit: def456abc789012345678901234567890abcdef'''
    }

    def 'generateTransformSourceCode field order is preserved'() {
        given: 'metadata with all fields'
        def metadata = new LaminRunManager.TransformMetadata(
            repository: 'https://github.com/test/repo',
            mainScript: 'workflow.nf',
            revision: 'develop',
            commitId: '1234567890abcdef1234567890abcdef12345678',
            entrypoint: 'MY_ENTRY',
            projectDir: Paths.get('/tmp'),
            manifestName: 'test',
            manifestDescription: 'test',
            gitInfo: null
        )

        when:
        String sourceCode = LaminRunManager.generateTransformSourceCode(metadata)
        List<String> lines = sourceCode.split('\n') as List

        then: 'fields appear in correct order: repo, path, entrypoint, commit/branch'
        lines[0].startsWith('repo:')
        lines[1].startsWith('path:')
        lines[2].startsWith('entrypoint:')
        lines[3].startsWith('commit:')
    }

    def 'generateTransformSourceCode without entrypoint omits entrypoint field'() {
        given: 'metadata without entrypoint'
        def metadata = new LaminRunManager.TransformMetadata(
            repository: 'https://github.com/test/repo',
            mainScript: 'main.nf',
            revision: 'main',
            commitId: 'abc123',
            entrypoint: null,
            projectDir: Paths.get('/tmp'),
            manifestName: 'test',
            manifestDescription: 'test',
            gitInfo: null
        )

        when:
        String sourceCode = LaminRunManager.generateTransformSourceCode(metadata)

        then:
        !sourceCode.contains('entrypoint:')
        sourceCode.split('\n').size() == 3  // repo, path, commit
    }

    def 'generateTransformSourceCode uses branch when gitInfo indicates branch tracking'() {
        given: 'metadata with gitInfo indicating a branch (not tag/commit)'
        def gitInfo = new GitHelper.GitInfo(
            provider: GitHelper.GitProvider.GITHUB,
            isTag: false,
            isCommit: false,
            isBranch: true
        )
        def metadata = new LaminRunManager.TransformMetadata(
            repository: 'https://github.com/test/repo',
            mainScript: 'main.nf',
            revision: 'main',
            commitId: 'abc123',  // Has commitId but gitInfo says it's a branch
            entrypoint: null,
            projectDir: Paths.get('/tmp'),
            manifestName: 'test',
            manifestDescription: 'test',
            gitInfo: gitInfo
        )

        when:
        String sourceCode = LaminRunManager.generateTransformSourceCode(metadata)

        then: 'uses branch instead of commit because gitInfo.isTag=false and gitInfo.isCommit=false'
        sourceCode.contains('branch: main')
        !sourceCode.contains('commit:')
    }

    def 'generateTransformSourceCode uses commit when gitInfo indicates tag'() {
        given: 'metadata with gitInfo indicating a tag'
        def gitInfo = new GitHelper.GitInfo(
            provider: GitHelper.GitProvider.GITHUB,
            isTag: true,
            isCommit: false,
            isBranch: false
        )
        def metadata = new LaminRunManager.TransformMetadata(
            repository: 'https://github.com/test/repo',
            mainScript: 'main.nf',
            revision: 'v1.0.0',
            commitId: 'abc123def456',
            entrypoint: null,
            projectDir: Paths.get('/tmp'),
            manifestName: 'test',
            manifestDescription: 'test',
            gitInfo: gitInfo
        )

        when:
        String sourceCode = LaminRunManager.generateTransformSourceCode(metadata)

        then: 'uses commit because gitInfo.isTag=true'
        sourceCode.contains('commit: abc123def456')
        !sourceCode.contains('branch:')
    }

    def 'generateTransformSourceCode handles nested script paths'() {
        given: 'metadata with nested script path'
        def metadata = new LaminRunManager.TransformMetadata(
            repository: 'https://github.com/org/monorepo',
            mainScript: 'pipelines/rnaseq/main.nf',
            revision: 'main',
            commitId: null,
            entrypoint: null,
            projectDir: Paths.get('/tmp'),
            manifestName: 'test',
            manifestDescription: 'test',
            gitInfo: null
        )

        when:
        String sourceCode = LaminRunManager.generateTransformSourceCode(metadata)

        then:
        sourceCode.contains('path: pipelines/rnaseq/main.nf')
    }

    def 'generateTransformSourceCode handles different git providers'() {
        given: 'metadata with GitLab repository'
        def metadata = new LaminRunManager.TransformMetadata(
            repository: 'https://gitlab.com/myorg/myrepo',
            mainScript: 'main.nf',
            revision: 'main',
            commitId: 'abc123',
            entrypoint: null,
            projectDir: Paths.get('/tmp'),
            manifestName: 'test',
            manifestDescription: 'test',
            gitInfo: null
        )

        when:
        String sourceCode = LaminRunManager.generateTransformSourceCode(metadata)

        then:
        sourceCode.startsWith('repo: https://gitlab.com/myorg/myrepo')
    }
}
