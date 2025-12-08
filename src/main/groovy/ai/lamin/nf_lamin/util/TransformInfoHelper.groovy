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
import java.util.LinkedHashMap

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.script.WorkflowMetadata

/**
 * Utility class for collecting and generating transform metadata from Nextflow sessions.
 *
 * This class handles:
 * - Extracting workflow metadata from Nextflow sessions
 * - Detecting git revision types (tag/branch/commit) from repositories
 * - Generating transform keys, descriptions, and source_code strings
 *
 * The source_code format matches lamindb's Transform.from_git() Python implementation:
 * - Pinned transforms (TAG/COMMIT): use "commit:" for immutable code references
 * - Sliding transforms (BRANCH): use "branch:" for mutable code references
 */
@Slf4j
@CompileStatic
class TransformInfoHelper {

    /**
     * Enumeration of git revision types.
     * Matches the semantics of Nextflow's AssetManager.RevisionInfo.Type
     */
    static enum RevisionType {
        /** A git tag - points to a specific commit, immutable */
        TAG,
        /** A git branch - can advance over time */
        BRANCH,
        /** A specific commit hash - detached HEAD state */
        COMMIT
    }

    /**
     * Data structure holding all metadata needed for transform creation
     */
    static class TransformMetadata {
        /** Repository name or project name */
        String repository

        /** Main script file path (relative to project directory) */
        String mainScript

        /** Revision name (branch/tag/commit) */
        String revision

        /** Commit ID (if available) */
        String commitId

        /** Entrypoint name (if specified via -entry) */
        String entrypoint

        /** Project directory path */
        Path projectDir

        /** Workflow manifest name */
        String manifestName

        /** Workflow manifest description */
        String manifestDescription

        /** Type of git revision (tag, branch, or commit) - null if not a git repo */
        RevisionType revisionType
    }

    /**
     * Collect all metadata needed for transform creation from the session
     *
     * @param session The Nextflow session
     * @return TransformMetadata containing all relevant information
     */
    static TransformMetadata collect(Session session) {
        WorkflowMetadata wfMetadata = session.getWorkflowMetadata()

        TransformMetadata metadata = new TransformMetadata()

        // Extract basic workflow metadata
        metadata.repository = wfMetadata.repository ?: wfMetadata.projectName
        metadata.mainScript = wfMetadata.scriptFile.toString().replaceFirst("${wfMetadata.projectDir}/", '')
        metadata.revision = wfMetadata.revision ?: 'local-development'
        metadata.commitId = wfMetadata.commitId
        metadata.projectDir = wfMetadata.projectDir

        // Extract entrypoint from session binding
        metadata.entrypoint = session?.getBinding()?.getEntryName()

        // Extract manifest information
        metadata.manifestName = wfMetadata.manifest.getName() ?: '<No name in manifest>'
        metadata.manifestDescription = wfMetadata.manifest.getDescription() ?: '<No description in manifest>'

        // Detect revision type from git repository
        if (metadata.commitId && metadata.projectDir) {
            metadata.revisionType = detectRevisionType(metadata.projectDir, metadata.revision, metadata.commitId)
        }

        return metadata
    }

    /**
     * Detect the type of git revision (tag, branch, or commit) by inspecting the git repository.
     *
     * This is necessary because Nextflow's WorkflowMetadata only exposes commitId and revision name,
     * but not the revision type. The ScriptFile.revisionInfo has this information, but it's not
     * accessible to plugins through the WorkflowMetadata API.
     *
     * @param projectDir The project directory containing the .git folder
     * @param revision The revision name (branch name, tag name, or commit hash)
     * @param commitId The full commit hash
     * @return The detected RevisionType, or null if detection fails
     */
    static RevisionType detectRevisionType(Path projectDir, String revision, String commitId) {
        if (!projectDir || !revision) {
            return null
        }

        try {
            Path gitDir = projectDir.resolve('.git')
            if (!Files.isDirectory(gitDir)) {
                log.debug "No .git directory found in ${projectDir}"
                return null
            }

            // Check if revision is a tag
            Path tagRef = gitDir.resolve("refs/tags/${revision}")
            if (Files.exists(tagRef)) {
                log.debug "Revision '${revision}' is a tag"
                return RevisionType.TAG
            }

            // Check packed-refs for tags (git may pack refs for efficiency)
            Path packedRefs = gitDir.resolve('packed-refs')
            String packedContent = null
            if (Files.exists(packedRefs)) {
                packedContent = packedRefs.text
                if (packedContent.contains("refs/tags/${revision}")) {
                    log.debug "Revision '${revision}' is a tag (found in packed-refs)"
                    return RevisionType.TAG
                }
            }

            // Check if revision is a branch
            Path branchRef = gitDir.resolve("refs/heads/${revision}")
            if (Files.exists(branchRef)) {
                log.debug "Revision '${revision}' is a branch"
                return RevisionType.BRANCH
            }

            // Check packed-refs for branches
            if (packedContent?.contains("refs/heads/${revision}")) {
                log.debug "Revision '${revision}' is a branch (found in packed-refs)"
                return RevisionType.BRANCH
            }

            // Check for remote branches (origin/branch-name)
            Path remoteBranchRef = gitDir.resolve("refs/remotes/origin/${revision}")
            if (Files.exists(remoteBranchRef)) {
                log.debug "Revision '${revision}' is a remote branch"
                return RevisionType.BRANCH
            }

            // If revision looks like a commit hash (40 hex chars or abbreviated)
            if (revision ==~ /^[0-9a-fA-F]{7,40}$/) {
                log.debug "Revision '${revision}' appears to be a commit hash"
                return RevisionType.COMMIT
            }

            // If revision contains git revision notation (^ or ~), it's a detached HEAD
            // pointing to a specific commit (e.g., "4.1.0^2^2" means "2 commits before 4.1.0")
            if (revision.contains('^') || revision.contains('~')) {
                log.debug "Revision '${revision}' contains git ancestry notation, treating as COMMIT"
                return RevisionType.COMMIT
            }

            // Default: if we have a commitId but can't identify as tag/branch, treat as COMMIT
            // This is safer than assuming BRANCH, because unknown revisions with a commitId
            // are more likely to be pinned to a specific commit (e.g., PR refs, detached HEAD)
            log.debug "Could not determine revision type for '${revision}', defaulting to COMMIT (has commitId)"
            return RevisionType.COMMIT

        } catch (Exception e) {
            log.debug "Failed to detect revision type: ${e.message}"
            return null
        }
    }

    /**
     * Generate the transform key from metadata
     * Format: "repository" or "repository:script" if not main.nf
     *
     * @param metadata Transform metadata
     * @return The transform key
     */
    static String generateTransformKey(TransformMetadata metadata) {
        return metadata.mainScript == 'main.nf'
            ? metadata.repository
            : "${metadata.repository}:${metadata.mainScript}"
    }

    /**
     * Generate the transform description from metadata
     *
     * @param metadata Transform metadata
     * @return The transform description
     */
    static String generateTransformDescription(TransformMetadata metadata) {
        return "${metadata.manifestName}: ${metadata.manifestDescription}"
    }

    /**
     * Generates source_code string in YAML-like format to match Python package.
     * Format: key: value pairs separated by newlines
     * See: lamindb/models/transform.py Transform.from_git()
     * https://github.com/laminlabs/lamindb/blob/734367caa7b4bff5216f4aca1d8e43fe88bb8b0a/lamindb/models/transform.py#L473-L489
     *
     * The Python implementation distinguishes between:
     * - "Pinned transforms": Tag or specific commit - code is immutable, use commit:
     * - "Sliding transforms": Branch - code can change over time, use branch:
     *
     * In Nextflow context:
     * - TAG: Use commit: (pinned - tags point to immutable code)
     * - COMMIT: Use commit: (pinned - specific commit is immutable)
     * - BRANCH: Use branch: (sliding - branch can advance)
     * - null revisionType with commitId: Default to commit: (safer assumption)
     * - null revisionType without commitId: Use branch: (local development)
     *
     * @param metadata Transform metadata containing all necessary information
     * @return Formatted source code string
     */
    static String generateTransformSourceCode(TransformMetadata metadata) {
        // Use LinkedHashMap to preserve insertion order
        Map<String, String> sourceData = new LinkedHashMap<>()

        // Repository and path are required
        sourceData.put('repo', metadata.repository)
        sourceData.put('path', metadata.mainScript)

        // Add entrypoint if specified (via -entry option)
        if (metadata.entrypoint) {
            sourceData.put('entrypoint', metadata.entrypoint)
        }

        // Determine whether to use commit: (pinned) or branch: (sliding)
        // based on the revision type detected from the git repository
        if (metadata.revisionType == RevisionType.BRANCH) {
            // Sliding transform: branch can advance, track by branch name
            sourceData.put('branch', metadata.revision)
        } else if (metadata.commitId) {
            // Pinned transform: TAG, COMMIT, or unknown type with commitId
            // Use commit hash for immutability
            sourceData.put('commit', metadata.commitId)
        } else if (metadata.revision) {
            // No commitId and no revisionType - local development without git
            sourceData.put('branch', metadata.revision)
        }

        // Convert map to YAML-like format: "key: value\n"
        return sourceData.collect { k, v -> "${k}: ${v}" }.join('\n')
    }
}
