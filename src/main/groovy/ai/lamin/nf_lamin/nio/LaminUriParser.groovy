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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Parser for Lamin URIs.
 *
 * Parses URIs of the format: lamin://owner/instance/artifact/uid[/subpath]
 *
 * Examples:
 * - lamin://laminlabs/lamindata/artifact/s3rtK8wIzJNKvg5Q
 * - lamin://laminlabs/lamindata/artifact/s3rtK8wIzJNKvg5Q/subdir/file.txt
 *
 * @author Lamin Labs
 */
@Slf4j
@CompileStatic
class LaminUriParser {

    static final String SCHEME = 'lamin'
    static final String SEP = '/'

    /**
     * The owner of the LaminDB instance (e.g., "laminlabs")
     */
    final String owner

    /**
     * The name of the LaminDB instance (e.g., "lamindata")
     */
    final String instance

    /**
     * The resource type (e.g., "artifact" or "storage" in future)
     */
    final String resourceType

    /**
     * The resource identifier (e.g., artifact UID "s3rtK8wIzJNKvg5Q")
     */
    final String resourceId

    /**
     * Optional sub-path within the artifact (for directories)
     */
    final String subPath

    /**
     * Private constructor - use parse() factory methods instead.
     */
    private LaminUriParser(String owner, String instance, String resourceType, String resourceId, String subPath) {
        this.owner = owner
        this.instance = instance
        this.resourceType = resourceType
        this.resourceId = resourceId
        this.subPath = subPath
    }

    /**
     * Parse a URI string into a LaminUriParser.
     *
     * @param uriString The URI string to parse (e.g., "lamin://laminlabs/lamindata/artifact/uid")
     * @return A LaminUriParser instance
     * @throws IllegalArgumentException if the URI is invalid
     */
    static LaminUriParser parse(String uriString) {
        if (!uriString?.trim()) {
            throw new IllegalArgumentException("URI string cannot be null or empty")
        }
        return parse(new URI(uriString))
    }

    /**
     * Parse a URI into a LaminUriParser.
     *
     * @param uri The URI to parse
     * @return A LaminUriParser instance
     * @throws IllegalArgumentException if the URI is invalid
     */
    static LaminUriParser parse(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null")
        }

        // Validate scheme
        String scheme = uri.scheme?.toLowerCase()
        if (scheme != SCHEME) {
            throw new IllegalArgumentException("Invalid scheme '${scheme}'. Expected '${SCHEME}'")
        }

        // Get the path part (everything after lamin://)
        // URI path starts with / so we need to handle that
        String path = uri.schemeSpecificPart
        if (path?.startsWith('//')) {
            path = path.substring(2)  // Remove leading //
        }

        if (!path?.trim()) {
            throw new IllegalArgumentException("URI path cannot be empty")
        }

        // Split path into components
        String[] parts = path.split(SEP)

        // Validate minimum components: owner/instance/resourceType/resourceId
        if (parts.length < 4) {
            throw new IllegalArgumentException(
                "Invalid URI format. Expected: lamin://owner/instance/resourceType/resourceId[/subpath]. " +
                "Got: ${uri}"
            )
        }

        String owner = parts[0]
        String instance = parts[1]
        String resourceType = parts[2]
        String resourceId = parts[3]

        // Validate components
        if (!owner?.trim()) {
            throw new IllegalArgumentException("Owner cannot be empty in URI: ${uri}")
        }
        if (!instance?.trim()) {
            throw new IllegalArgumentException("Instance cannot be empty in URI: ${uri}")
        }
        if (!resourceType?.trim()) {
            throw new IllegalArgumentException("Resource type cannot be empty in URI: ${uri}")
        }
        if (!resourceId?.trim()) {
            throw new IllegalArgumentException("Resource ID cannot be empty in URI: ${uri}")
        }

        // Validate resource type
        if (resourceType != 'artifact') {
            throw new IllegalArgumentException(
                "Unsupported resource type '${resourceType}'. Currently only 'artifact' is supported."
            )
        }

        // Collect sub-path if present
        String subPath = null
        if (parts.length > 4) {
            subPath = parts[4..-1].join(SEP)
        }

        log.trace "Parsed URI: owner=${owner}, instance=${instance}, resourceType=${resourceType}, resourceId=${resourceId}, subPath=${subPath}"

        return new LaminUriParser(owner, instance, resourceType, resourceId, subPath)
    }

    /**
     * Get the instance slug in format "owner/instance"
     */
    String getInstanceSlug() {
        return "${owner}/${instance}"
    }

    /**
     * Check if this URI has a sub-path
     */
    boolean hasSubPath() {
        return subPath != null && !subPath.isEmpty()
    }

    /**
     * Convert back to a URI string
     */
    String toUriString() {
        StringBuilder sb = new StringBuilder()
        sb.append(SCHEME).append('://').append(owner).append(SEP).append(instance)
        sb.append(SEP).append(resourceType).append(SEP).append(resourceId)
        if (hasSubPath()) {
            sb.append(SEP).append(subPath)
        }
        return sb.toString()
    }

    /**
     * Convert to a URI object
     */
    URI toUri() {
        return new URI(toUriString())
    }

    /**
     * Create a new LaminUriParser with an appended sub-path
     */
    LaminUriParser withSubPath(String additionalPath) {
        if (!additionalPath?.trim()) {
            return this
        }
        String newSubPath = hasSubPath() ? "${subPath}/${additionalPath}" : additionalPath
        return new LaminUriParser(owner, instance, resourceType, resourceId, newSubPath)
    }

    /**
     * Create a new LaminUriParser with the sub-path removed
     */
    LaminUriParser withoutSubPath() {
        return new LaminUriParser(owner, instance, resourceType, resourceId, null)
    }

    /**
     * Get the last component of the sub-path (filename)
     */
    String getFileName() {
        if (!hasSubPath()) {
            return resourceId
        }
        int lastSep = subPath.lastIndexOf(SEP)
        return lastSep >= 0 ? subPath.substring(lastSep + 1) : subPath
    }

    /**
     * Get the parent path (sub-path without the last component)
     */
    LaminUriParser getParent() {
        if (!hasSubPath()) {
            return null
        }
        int lastSep = subPath.lastIndexOf(SEP)
        if (lastSep <= 0) {
            return new LaminUriParser(owner, instance, resourceType, resourceId, null)
        }
        return new LaminUriParser(owner, instance, resourceType, resourceId, subPath.substring(0, lastSep))
    }

    @Override
    String toString() {
        return toUriString()
    }

    @Override
    boolean equals(Object obj) {
        if (this.is(obj)) return true
        if (!(obj instanceof LaminUriParser)) return false
        LaminUriParser other = (LaminUriParser) obj
        return owner == other.owner &&
               instance == other.instance &&
               resourceType == other.resourceType &&
               resourceId == other.resourceId &&
               subPath == other.subPath
    }

    @Override
    int hashCode() {
        return Objects.hash(owner, instance, resourceType, resourceId, subPath)
    }
}
