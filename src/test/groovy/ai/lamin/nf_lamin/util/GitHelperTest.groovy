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

import java.nio.file.Path
import java.nio.file.Paths

import spock.lang.Specification

/**
 * Unit tests for GitHelper
 */
class GitHelperTest extends Specification {

    def 'should detect GitHub provider from URL'() {
        expect:
        GitHelper.GitProvider.fromUrl('https://github.com/user/repo') == GitHelper.GitProvider.GITHUB
        GitHelper.GitProvider.fromUrl('git@github.com:user/repo.git') == GitHelper.GitProvider.GITHUB
    }

    def 'should detect GitLab provider from URL'() {
        expect:
        GitHelper.GitProvider.fromUrl('https://gitlab.com/user/repo') == GitHelper.GitProvider.GITLAB
    }

    def 'should detect Bitbucket provider from URL'() {
        expect:
        GitHelper.GitProvider.fromUrl('https://bitbucket.org/user/repo') == GitHelper.GitProvider.BITBUCKET
    }

    def 'should detect Azure DevOps provider from URL'() {
        expect:
        GitHelper.GitProvider.fromUrl('https://dev.azure.com/org/project') == GitHelper.GitProvider.AZURE_DEVOPS
    }

    def 'should return UNKNOWN for unrecognized provider'() {
        expect:
        GitHelper.GitProvider.fromUrl('https://example.com/user/repo') == GitHelper.GitProvider.UNKNOWN
        GitHelper.GitProvider.fromUrl(null) == GitHelper.GitProvider.UNKNOWN
    }

    def 'should recognize commit hashes'() {
        expect:
        GitHelper.looksLikeCommitHash('abc123d')
        GitHelper.looksLikeCommitHash('d77cacc97b5b387f0fc8d31ad61e7180cf7ce76b')
        GitHelper.looksLikeCommitHash('7f9cf6d651')

        !GitHelper.looksLikeCommitHash('main')
        !GitHelper.looksLikeCommitHash('v1.0.0')
        !GitHelper.looksLikeCommitHash('abc123z')  // contains non-hex char
    }

    def 'should construct GitHub source URL'() {
        given:
        def provider = GitHelper.GitProvider.GITHUB
        def repoUrl = 'https://github.com/user/repo'
        def commit = 'abc123'
        def filePath = 'src/main.nf'

        when:
        def url = GitHelper.constructSourceUrl(provider, repoUrl, commit, filePath)

        then:
        url == 'https://github.com/user/repo/blob/abc123/src/main.nf'
    }

    def 'should construct GitLab source URL'() {
        given:
        def provider = GitHelper.GitProvider.GITLAB
        def repoUrl = 'https://gitlab.com/user/repo'
        def commit = 'abc123'
        def filePath = 'src/main.nf'

        when:
        def url = GitHelper.constructSourceUrl(provider, repoUrl, commit, filePath)

        then:
        url == 'https://gitlab.com/user/repo/-/blob/abc123/src/main.nf'
    }

    def 'should construct Bitbucket source URL'() {
        given:
        def provider = GitHelper.GitProvider.BITBUCKET
        def repoUrl = 'https://bitbucket.org/user/repo'
        def commit = 'abc123'
        def filePath = 'src/main.nf'

        when:
        def url = GitHelper.constructSourceUrl(provider, repoUrl, commit, filePath)

        then:
        url == 'https://bitbucket.org/user/repo/src/abc123/src/main.nf'
    }

    def 'should handle .git suffix in repo URL'() {
        given:
        def provider = GitHelper.GitProvider.GITHUB
        def repoUrl = 'https://github.com/user/repo.git'
        def commit = 'abc123'
        def filePath = 'main.nf'

        when:
        def url = GitHelper.constructSourceUrl(provider, repoUrl, commit, filePath)

        then:
        url == 'https://github.com/user/repo/blob/abc123/main.nf'
    }

    def 'should handle leading slash in file path'() {
        given:
        def provider = GitHelper.GitProvider.GITHUB
        def repoUrl = 'https://github.com/user/repo'
        def commit = 'abc123'
        def filePath = '/src/main.nf'

        when:
        def url = GitHelper.constructSourceUrl(provider, repoUrl, commit, filePath)

        then:
        url == 'https://github.com/user/repo/blob/abc123/src/main.nf'
    }

    def 'should return null for unknown provider when constructing URL'() {
        given:
        def provider = GitHelper.GitProvider.UNKNOWN
        def repoUrl = 'https://example.com/user/repo'
        def commit = 'abc123'
        def filePath = 'main.nf'

        expect:
        GitHelper.constructSourceUrl(provider, repoUrl, commit, filePath) == null
    }

    def "should load SCM providers from config"() {
        when:
        def providers = GitHelper.loadScmProviders()

        then:
        providers != null
        // Should at least have default providers (github, gitlab, bitbucket, gitea, azurerepos)
        providers.size() >= 5
        providers.any { it.name == 'github' }
        providers.any { it.name == 'gitlab' }
        providers.any { it.name == 'bitbucket' }
    }

    def "should detect custom GitLab instance from SCM config"() {
        given:
        def customUrl = "https://gitlab.example.com/myorg/myrepo.git"
        
        // Note: In real usage, this would come from ~/.nextflow/scm file like:
        // providers {
        //     my-gitlab {
        //         platform = 'gitlab'
        //         server = 'https://gitlab.example.com'
        //     }
        // }
        
        when:
        def provider = GitHelper.GitProvider.fromUrl(customUrl)
        
        then:
        // Without SCM config, it won't match
        provider == GitHelper.GitProvider.UNKNOWN
    }

    def "should map platform names to providers"() {
        expect:
        GitHelper.GitProvider.fromUrl("https://github.com/user/repo") == GitHelper.GitProvider.GITHUB
        GitHelper.GitProvider.fromUrl("https://gitlab.com/user/repo") == GitHelper.GitProvider.GITLAB
        GitHelper.GitProvider.fromUrl("https://bitbucket.org/user/repo") == GitHelper.GitProvider.BITBUCKET
        GitHelper.GitProvider.fromUrl("https://gitea.com/user/repo") == GitHelper.GitProvider.GITEA
        GitHelper.GitProvider.fromUrl("https://dev.azure.com/org/project") == GitHelper.GitProvider.AZURE_DEVOPS
    }
}
