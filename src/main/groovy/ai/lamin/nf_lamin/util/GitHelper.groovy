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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.scm.ProviderConfig

/**
 * Utility class to extract git repository information that isn't exposed
 * through Nextflow's WorkflowMetadata API.
 * 
 * This helper reads the .git directory directly to determine:
 * - Git provider (GitHub, GitLab, Bitbucket, etc.)
 * - Whether a revision is a tag or branch
 * - Remote repository URLs
 * 
 * It can also leverage Nextflow's ~/.nextflow/scm configuration file to
 * properly identify custom Git providers (self-hosted GitLab, Bitbucket, etc.)
 * 
 * @author Robrecht Cannoodt
 */
@Slf4j
@CompileStatic
class GitHelper {

    /**
     * Supported git providers
     */
    enum GitProvider {
        GITHUB('github.com', 'github'),
        GITLAB('gitlab.com', 'gitlab'),
        BITBUCKET('bitbucket.org', 'bitbucket'),
        GITEA('gitea', 'gitea'),
        AZURE_DEVOPS('dev.azure.com', 'azure'),
        UNKNOWN(null, 'unknown')

        final String domain
        final String name

        GitProvider(String domain, String name) {
            this.domain = domain
            this.name = name
        }

        static GitProvider fromUrl(String url) {
            return fromUrl(url, null)
        }

        /**
         * Detect provider from URL, optionally using Nextflow's SCM configuration
         * 
         * @param url The remote URL
         * @param scmProviders Optional list of configured providers from ~/.nextflow/scm
         * @return The detected provider
         */
        static GitProvider fromUrl(String url, List<ProviderConfig> scmProviders) {
            if (!url) return UNKNOWN
            
            // First try to match using Nextflow's SCM config if available
            if (scmProviders) {
                for (ProviderConfig config : scmProviders) {
                    String domain = config.domain
                    if (domain && url.contains(domain)) {
                        // Map Nextflow's platform names to our enum
                        return fromPlatformName(config.platform)
                    }
                }
            }
            
            // Fallback to simple domain matching
            for (GitProvider provider : values()) {
                if (provider.domain && url.contains(provider.domain)) {
                    return provider
                }
            }
            return UNKNOWN
        }

        /**
         * Map Nextflow's platform names to GitProvider enum
         */
        private static GitProvider fromPlatformName(String platform) {
            switch (platform) {
                case 'github':
                    return GITHUB
                case 'gitlab':
                    return GITLAB
                case 'bitbucket':
                case 'bitbucketserver':
                    return BITBUCKET
                case 'gitea':
                    return GITEA
                case 'azurerepos':
                    return AZURE_DEVOPS
                default:
                    return UNKNOWN
            }
        }

        /**
         * Get the URL path format for linking to source code
         * @param commitOrRef The commit hash or reference (tag/branch)
         * @return The path component (e.g., "blob", "-/blob", "src")
         */
        String getSourcePathFormat(String commitOrRef) {
            switch (this) {
                case GITHUB:
                    return "blob/${commitOrRef}"
                case GITLAB:
                    return "-/blob/${commitOrRef}"
                case BITBUCKET:
                    return "src/${commitOrRef}"
                case GITEA:
                    return "src/commit/${commitOrRef}"
                case AZURE_DEVOPS:
                    return "_git?version=GC${commitOrRef}"
                default:
                    return null
            }
        }
    }

    /**
     * Information extracted from a git repository
     */
    static class GitInfo {
        /** The git provider (GitHub, GitLab, etc.) */
        GitProvider provider = GitProvider.UNKNOWN
        
        /** Remote repository URL */
        String remoteUrl
        
        /** Whether the revision appears to be a tag */
        boolean isTag = false
        
        /** Whether the revision appears to be a branch */
        boolean isBranch = false
        
        /** Whether the revision appears to be a commit hash */
        boolean isCommit = false
        
        /** List of all tags in the repository */
        List<String> tags = []
        
        /** List of all branches in the repository */
        List<String> branches = []
        
        /** The HEAD reference (current branch or commit) */
        String headRef
        
        @Override
        String toString() {
            return "GitInfo(provider=${provider.name}, remoteUrl=${remoteUrl}, " +
                   "isTag=${isTag}, isBranch=${isBranch}, isCommit=${isCommit})"
        }
    }

    /**
     * Load SCM provider configurations from Nextflow's config file
     * Reads from ~/.nextflow/scm or $NXF_SCM_FILE
     * 
     * @return List of configured providers, or empty list if config doesn't exist
     */
    @groovy.transform.CompileStatic(groovy.transform.TypeCheckingMode.SKIP)
    static List<ProviderConfig> loadScmProviders() {
        try {
            // Use Nextflow's built-in method to get and parse SCM config
            Map config = ProviderConfig.getDefault()
            List<ProviderConfig> result = ProviderConfig.createFromMap(config)
            return result
        } catch (Exception e) {
            log.debug "Failed to load SCM config: ${e.message}"
            return []
        }
    }

    /**
     * Check if a project directory has a git repository
     * 
     * @param projectDir The project directory path
     * @return true if .git directory exists
     */
    static boolean hasGitRepo(Path projectDir) {
        if (!projectDir) return false
        Path gitDir = projectDir.resolve('.git')
        return Files.exists(gitDir) && Files.isDirectory(gitDir)
    }

    /**
     * Extract git information from a project directory
     * 
     * @param projectDir The project directory path
     * @param revisionName The revision name from WorkflowMetadata (optional)
     * @param useScmConfig Whether to load and use Nextflow's SCM configuration
     * @return GitInfo object containing extracted information, or null if no git repo
     */
    static GitInfo getGitInfo(Path projectDir, String revisionName = null, boolean useScmConfig = true) {
        if (!hasGitRepo(projectDir)) {
            log.debug "No git repository found at ${projectDir}"
            return null
        }

        GitInfo info = new GitInfo()
        
        try {
            // Load SCM providers if requested
            List<ProviderConfig> scmProviders = useScmConfig ? loadScmProviders() : []
            
            // Extract remote URL and provider
            String remoteUrl = getRemoteUrl(projectDir)
            if (remoteUrl) {
                info.remoteUrl = remoteUrl
                // Use SCM config for provider detection
                info.provider = GitProvider.fromUrl(remoteUrl, scmProviders)
            }

            // Get HEAD reference
            info.headRef = getHeadRef(projectDir)

            // Read tags and branches
            info.tags = getTags(projectDir)
            info.branches = getBranches(projectDir)

            // Determine revision type if revisionName is provided
            if (revisionName) {
                // Check actual git refs first - this is the source of truth
                info.isTag = info.tags.contains(revisionName)
                info.isBranch = info.branches.contains(revisionName)
                // If it's neither a known tag nor branch, check if it looks like a commit hash
                info.isCommit = !info.isTag && !info.isBranch && looksLikeCommitHash(revisionName)
            }

            log.debug "Extracted git info: ${info}"
            return info

        } catch (Exception e) {
            log.debug "Failed to extract git info from ${projectDir}: ${e.message}"
            return info
        }
    }

    /**
     * Get the remote URL from git config
     * 
     * @param projectDir The project directory
     * @return The remote URL, or null if not found
     */
    private static String getRemoteUrl(Path projectDir) {
        Path gitConfig = projectDir.resolve('.git/config')
        if (!Files.exists(gitConfig)) {
            return null
        }

        try {
            String configText = gitConfig.text
            
            // Find [remote "origin"] section and extract URL
            def matcher = configText =~ /\[remote "origin"\][^\[]*?url\s*=\s*(.+)/
            if (matcher.find()) {
                String url = matcher.group(1).trim()
                // Convert git@ URLs to https://
                if (url.startsWith('git@')) {
                    url = url.replaceFirst(/^git@/, 'https://')
                                .replaceFirst(/:/, '/')
                                .replaceFirst(/\.git$/, '')
                }
                return url
            }
        } catch (Exception e) {
            log.debug "Failed to read git config: ${e.message}"
        }

        return null
    }

    /**
     * Get the current HEAD reference (branch name or commit)
     * 
     * @param projectDir The project directory
     * @return The HEAD reference, or null if not found
     */
    private static String getHeadRef(Path projectDir) {
        Path headFile = projectDir.resolve('.git/HEAD')
        if (!Files.exists(headFile)) {
            return null
        }

        try {
            String headContent = headFile.text.trim()
            // If HEAD points to a branch: ref: refs/heads/main
            if (headContent.startsWith('ref:')) {
                return headContent.replaceFirst(/^ref:\s*refs\/heads\//, '')
            }
            // Otherwise it's a detached HEAD with commit hash
            return headContent
        } catch (Exception e) {
            log.debug "Failed to read HEAD file: ${e.message}"
            return null
        }
    }

    /**
     * Get list of all tags in the repository
     * 
     * @param projectDir The project directory
     * @return List of tag names
     */
    private static List<String> getTags(Path projectDir) {
        Path tagsDir = projectDir.resolve('.git/refs/tags')
        if (!Files.exists(tagsDir)) {
            return []
        }

        try {
            return Files.list(tagsDir)
                .filter { Files.isRegularFile(it) }
                .map { it.fileName.toString() }
                .collect() as List<String>
        } catch (Exception e) {
            log.debug "Failed to read tags: ${e.message}"
            return []
        }
    }

    /**
     * Get list of all local branches in the repository
     * 
     * @param projectDir The project directory
     * @return List of branch names
     */
    private static List<String> getBranches(Path projectDir) {
        Path branchesDir = projectDir.resolve('.git/refs/heads')
        if (!Files.exists(branchesDir)) {
            return []
        }

        try {
            return Files.walk(branchesDir)
                .filter { Files.isRegularFile(it) }
                .map { branchesDir.relativize(it).toString() }
                .collect() as List<String>
        } catch (Exception e) {
            log.debug "Failed to read branches: ${e.message}"
            return []
        }
    }

    /**
     * Check if a string looks like a git commit hash
     * 
     * @param value The value to check
     * @return true if it looks like a commit hash (40 hex chars or shortened)
     */
    static boolean looksLikeCommitHash(String value) {
        if (!value) return false
        return value ==~ /^[0-9a-f]{7,40}$/
    }

    /**
     * Construct a URL to the source code file for the given provider
     * 
     * @param provider The git provider
     * @param repoUrl The repository base URL (e.g., https://github.com/user/repo)
     * @param commitOrRef The commit hash or reference (tag/branch)
     * @param filePath The path to the file within the repo
     * @return The full URL to the file, or null if provider not supported
     */
    static String constructSourceUrl(GitProvider provider, String repoUrl, String commitOrRef, String filePath) {
        if (!provider || !repoUrl || !commitOrRef || !filePath) {
            return null
        }

        String pathFormat = provider.getSourcePathFormat(commitOrRef)
        if (!pathFormat) {
            return null
        }

        // Ensure repoUrl doesn't end with .git
        String baseUrl = repoUrl.replaceFirst(/\.git$/, '')
        
        // Ensure filePath doesn't start with /
        String cleanPath = filePath.startsWith('/') ? filePath.substring(1) : filePath

        return "${baseUrl}/${pathFormat}/${cleanPath}"
    }

    /**
     * Convenience method to construct source URL from GitInfo
     * 
     * @param gitInfo The git information
     * @param commitOrRef The commit hash or reference
     * @param filePath The path to the file
     * @return The full URL to the file, or null if not possible
     */
    static String constructSourceUrl(GitInfo gitInfo, String commitOrRef, String filePath) {
        if (!gitInfo || !gitInfo.remoteUrl) {
            return null
        }
        return constructSourceUrl(gitInfo.provider, gitInfo.remoteUrl, commitOrRef, filePath)
    }
}
