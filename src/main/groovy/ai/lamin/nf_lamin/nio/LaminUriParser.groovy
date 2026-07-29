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
 * A URI either points at an existing artifact (read-only) or at a location in an
 * instance's storage (writable):
 *
 * <pre>
 * lamin://owner/instance/artifact/uid[/subpath]        read an artifact
 * lamin://owner/instance?space=&storage=&prefix=       publish to a storage location
 * lamin://owner/instance/space/&lt;space&gt;?storage=&prefix=
 * lamin://owner/instance/storage/&lt;storage&gt;?prefix=
 * </pre>
 *
 * The three publish forms are equivalent; they are normalised to the same internal
 * representation and rendered back in a single canonical form, so that
 * {@code parse(x.toUriString()) == x} always holds.
 *
 * The key of a published file always lives in {@code prefix}, never in the path — that
 * keeps the path unambiguous, so a key starting with {@code artifact/} or {@code space/}
 * can never be mistaken for a selector.
 *
 * Examples:
 * - lamin://laminlabs/lamindata/artifact/s3rtK8wIzJNKvg5Q
 * - lamin://laminlabs/lamindata/artifact/s3rtK8wIzJNKvg5Q/subdir/file.txt
 * - lamin://laminlabs/lamindata?prefix=results
 * - lamin://laminlabs/lamindata/space/my-space?prefix=results/qc/report.html
 */
@Slf4j
@CompileStatic
class LaminUriParser {

    static final String SCHEME = 'lamin'
    static final String SEP = '/'

    /** Directory reserved by LaminDB for auto-managed artifacts; cannot be published into. */
    static final String AUTO_KEY_PREFIX = '.lamindb'

    static final String TYPE_ARTIFACT = 'artifact'
    static final String TYPE_SPACE = 'space'
    static final String TYPE_STORAGE = 'storage'

    private static final List<String> QUERY_PARAMS = [TYPE_SPACE, TYPE_STORAGE, 'prefix']

    /**
     * What this URI points at.
     */
    final LaminUriKind kind

    /**
     * The owner of the LaminDB instance (e.g., "laminlabs")
     */
    final String owner

    /**
     * The name of the LaminDB instance (e.g., "lamindata")
     */
    final String instance

    /**
     * The resource type: "artifact", "space", "storage", or null when the instance's
     * default storage is targeted.
     */
    final String resourceType

    /**
     * The resource identifier: an artifact UID, a space reference, or a storage reference.
     * Null when the instance's default storage is targeted.
     */
    final String resourceId

    /**
     * Optional sub-path within the artifact (for directories). Artifact URIs only.
     */
    final String subPath

    /**
     * Normalised object key relative to the storage root, or null for the storage root
     * itself. Storage URIs only.
     */
    final String key

    /**
     * Storage reference, set only when a space <em>and</em> a storage were both selected.
     * Storage URIs only.
     */
    final String storageRef

    /**
     * Private constructor - use parse() factory methods instead.
     */
    private LaminUriParser(LaminUriKind kind, String owner, String instance, String resourceType,
                           String resourceId, String subPath, String key, String storageRef) {
        this.kind = kind
        this.owner = owner
        this.instance = instance
        this.resourceType = resourceType
        this.resourceId = resourceId
        this.subPath = subPath
        this.key = key
        this.storageRef = storageRef
    }

    /**
     * Parse a URI string into a LaminUriParser.
     *
     * @param uriString The URI string (e.g., "lamin://laminlabs/lamindata/artifact/uid")
     * @return A LaminUriParser instance
     * @throws IllegalArgumentException if the URI is invalid
     */
    static LaminUriParser parse(String uriString) {
        if (!uriString?.trim()) {
            throw new IllegalArgumentException("URI string cannot be null or empty")
        }

        String remainder = uriString.trim()

        // Validate scheme
        int schemeEnd = remainder.indexOf(':')
        String scheme = schemeEnd > 0 ? remainder.substring(0, schemeEnd).toLowerCase() : null
        if (scheme != SCHEME) {
            throw new IllegalArgumentException("Invalid scheme '${scheme}'. Expected '${SCHEME}'")
        }
        remainder = remainder.substring(schemeEnd + 1)
        if (remainder.startsWith('//')) {
            remainder = remainder.substring(2)
        }

        // Split off the query string. The path is deliberately not run through java.net.URI:
        // that would decode %2F into a separator and corrupt the segment boundaries.
        String rawPath = remainder
        String rawQuery = null
        int queryStart = remainder.indexOf('?')
        if (queryStart >= 0) {
            rawPath = remainder.substring(0, queryStart)
            rawQuery = remainder.substring(queryStart + 1)
        }

        if (!rawPath?.trim()) {
            throw new IllegalArgumentException("URI path cannot be empty")
        }

        List<String> segments = rawPath.split(SEP).findAll { String s -> s } as List<String>
        Map<String, String> query = parseQuery(rawQuery, uriString)

        if (segments.size() < 2) {
            throw new IllegalArgumentException(
                "Invalid URI format. Expected at least: lamin://owner/instance. Got: ${uriString}"
            )
        }

        String owner = decode(segments[0])
        String instance = decode(segments[1])
        if (!owner?.trim()) {
            throw new IllegalArgumentException("Owner cannot be empty in URI: ${uriString}")
        }
        if (!instance?.trim()) {
            throw new IllegalArgumentException("Instance cannot be empty in URI: ${uriString}")
        }

        String type = segments.size() > 2 ? decode(segments[2]) : null

        if (type == TYPE_ARTIFACT) {
            return parseArtifact(uriString, owner, instance, segments, query)
        }
        if (type == null || type == TYPE_SPACE || type == TYPE_STORAGE) {
            return parseStorage(uriString, owner, instance, type, segments, query)
        }

        throw new IllegalArgumentException(
            "Unsupported resource type '${type}' in URI: ${uriString}. " +
            "Expected '${TYPE_ARTIFACT}', '${TYPE_SPACE}' or '${TYPE_STORAGE}'."
        )
    }

    private static LaminUriParser parseArtifact(String uriString, String owner, String instance,
                                                List<String> segments, Map<String, String> query) {
        if (query) {
            throw new IllegalArgumentException(
                "Query parameters are not supported for artifact URIs: ${uriString}"
            )
        }
        if (segments.size() < 4) {
            throw new IllegalArgumentException(
                "Invalid URI format. Expected: lamin://owner/instance/artifact/uid[/subpath]. " +
                "Got: ${uriString}"
            )
        }

        String resourceId = decode(segments[3])
        if (!resourceId?.trim()) {
            throw new IllegalArgumentException("Resource ID cannot be empty in URI: ${uriString}")
        }

        String subPath = segments.size() > 4
            ? segments[4..-1].collect { String s -> decode(s) }.join(SEP)
            : null

        log.trace "Parsed artifact URI: owner=${owner}, instance=${instance}, uid=${resourceId}, subPath=${subPath}"

        return new LaminUriParser(LaminUriKind.ARTIFACT, owner, instance, TYPE_ARTIFACT, resourceId, subPath, null, null)
    }

    private static LaminUriParser parseStorage(String uriString, String owner, String instance, String type,
                                               List<String> segments, Map<String, String> query) {
        String pathRef = null
        if (type != null) {
            if (segments.size() < 4) {
                throw new IllegalArgumentException(
                    "Invalid URI format. Expected: lamin://owner/instance/${type}/<${type}>. Got: ${uriString}"
                )
            }
            if (segments.size() > 4) {
                throw new IllegalArgumentException(
                    "A publish key must be given as '?prefix=...', not as path segments. Got: ${uriString}"
                )
            }
            pathRef = decode(segments[3])
            if (!pathRef?.trim()) {
                throw new IllegalArgumentException("${type.capitalize()} reference cannot be empty in URI: ${uriString}")
            }
            if (query.containsKey(type)) {
                throw new IllegalArgumentException(
                    "Duplicate ${type} selector in URI: ${uriString}. " +
                    "Use either the path form or '?${type}=', not both."
                )
            }
        }

        String spaceRef = validateUid(TYPE_SPACE, type == TYPE_SPACE ? pathRef : query.get(TYPE_SPACE), uriString)
        String storageRef = validateUid(TYPE_STORAGE, type == TYPE_STORAGE ? pathRef : query.get(TYPE_STORAGE), uriString)
        String key = normalizeKey(query.get('prefix'))

        // Canonical form: a space takes the path slot when present (mirroring LaminDB, where the
        // space determines the storage location), otherwise the storage does
        String resourceType = null
        String resourceId = null
        String extraStorageRef = null
        if (spaceRef) {
            resourceType = TYPE_SPACE
            resourceId = spaceRef
            extraStorageRef = storageRef
        }
        else if (storageRef) {
            resourceType = TYPE_STORAGE
            resourceId = storageRef
        }

        log.trace "Parsed storage URI: owner=${owner}, instance=${instance}, ${resourceType}=${resourceId}, key=${key}"

        return new LaminUriParser(LaminUriKind.STORAGE, owner, instance, resourceType, resourceId, null, key, extraStorageRef)
    }

    /**
     * A space or storage is selected by its UID -- neither has a stable name to look up by
     * ({@code core.storage} has no name field at all), so anything path-like is a mistake.
     */
    private static String validateUid(String type, String value, String uriString) {
        if (value == null) {
            return null
        }
        if (value.contains(SEP) || value.contains(':')) {
            throw new IllegalArgumentException(
                "A ${type} must be selected by its UID, not by '${value}', in URI: ${uriString}"
            )
        }
        return value
    }

    /**
     * Parse and validate the query string. Unknown parameters are rejected: a typo in a
     * publish target must not silently publish somewhere else.
     */
    private static Map<String, String> parseQuery(String rawQuery, String uriString) {
        Map<String, String> result = [:]
        if (!rawQuery?.trim()) {
            return result
        }

        for (String pair : rawQuery.split('&')) {
            if (!pair) {
                continue
            }
            int eq = pair.indexOf('=')
            String name = decode(eq >= 0 ? pair.substring(0, eq) : pair)
            String value = eq >= 0 ? decode(pair.substring(eq + 1)) : ''
            if (!QUERY_PARAMS.contains(name)) {
                throw new IllegalArgumentException(
                    "Unknown query parameter '${name}' in URI: ${uriString}. " +
                    "Supported: ${QUERY_PARAMS.join(', ')}."
                )
            }
            if (value) {
                result.put(name, value)
            }
        }
        return result
    }

    /**
     * Normalise an object key: strip leading and trailing separators, collapse repeated
     * separators, drop '.' segments and resolve '..' segments.
     *
     * @return the normalised key, or null when it is empty
     * @throws IllegalArgumentException if the key escapes the storage root or is reserved
     */
    static String normalizeKey(String rawKey) {
        if (rawKey == null) {
            return null
        }
        String trimmed = rawKey.trim()
        if (!trimmed) {
            return null
        }

        List<String> segments = []
        for (String segment : trimmed.split(SEP)) {
            if (!segment || segment == '.') {
                continue
            }
            if (segment == '..') {
                if (!segments) {
                    throw new IllegalArgumentException("Key '${rawKey}' points outside of the storage root")
                }
                segments.removeAt(segments.size() - 1)
                continue
            }
            segments.add(segment)
        }

        if (!segments) {
            return null
        }
        if (segments[0] == AUTO_KEY_PREFIX) {
            throw new IllegalArgumentException(
                "'${AUTO_KEY_PREFIX}/' is reserved by LaminDB for auto-managed artifacts " +
                "and cannot be used as a publish key. Got: ${rawKey}"
            )
        }
        return segments.join(SEP)
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
        return parse(uri.toString())
    }

    /**
     * Get the instance slug in format "owner/instance"
     */
    String getInstanceSlug() {
        return "${owner}/${instance}"
    }

    /**
     * Whether this URI points at a storage location rather than an artifact.
     */
    boolean isStorage() {
        return kind == LaminUriKind.STORAGE
    }

    /**
     * Check if this URI has a sub-path
     */
    boolean hasSubPath() {
        return subPath != null && !subPath.isEmpty()
    }

    /**
     * Check if this URI has an object key
     */
    boolean hasKey() {
        return key != null && !key.isEmpty()
    }

    /**
     * The selected space reference, or null when no space was selected.
     */
    String getSpaceRef() {
        return resourceType == TYPE_SPACE ? resourceId : null
    }

    /**
     * The selected storage reference, or null when no storage was selected.
     */
    String getStorageReference() {
        return resourceType == TYPE_STORAGE ? resourceId : storageRef
    }

    /**
     * Convert back to a URI string, in canonical form.
     */
    String toUriString() {
        StringBuilder sb = new StringBuilder()
        sb.append(SCHEME).append('://').append(encodePath(owner)).append(SEP).append(encodePath(instance))

        if (kind == LaminUriKind.ARTIFACT) {
            sb.append(SEP).append(resourceType).append(SEP).append(encodePath(resourceId))
            if (hasSubPath()) {
                sb.append(SEP).append(encodePath(subPath))
            }
            return sb.toString()
        }

        // The space takes the path slot when present, mirroring LaminDB where the space
        // determines which storage location is used
        if (resourceType != null) {
            sb.append(SEP).append(resourceType).append(SEP).append(encodeSegment(resourceId))
        }

        List<String> params = []
        if (storageRef) {
            params.add("${TYPE_STORAGE}=${encodeSegment(storageRef)}".toString())
        }
        if (hasKey()) {
            params.add("prefix=${encodeQueryValue(key)}".toString())
        }
        if (params) {
            sb.append('?').append(params.join('&'))
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
     * Create a new LaminUriParser with an appended sub-path (artifact) or key segment (storage)
     */
    LaminUriParser withSubPath(String additionalPath) {
        if (!additionalPath?.trim()) {
            return this
        }
        if (kind == LaminUriKind.ARTIFACT) {
            String newSubPath = hasSubPath() ? "${subPath}/${additionalPath}" : additionalPath
            return new LaminUriParser(kind, owner, instance, resourceType, resourceId, newSubPath, null, null)
        }
        String combined = hasKey() ? "${key}/${additionalPath}".toString() : additionalPath
        String newKey = normalizeKey(combined)
        return new LaminUriParser(kind, owner, instance, resourceType, resourceId, null, newKey, storageRef)
    }

    /**
     * Create a new LaminUriParser pointing at the root: the artifact without its sub-path,
     * or the storage location without its key.
     */
    LaminUriParser withoutSubPath() {
        return new LaminUriParser(kind, owner, instance, resourceType, resourceId, null, null, storageRef)
    }

    /**
     * Get the last component of the sub-path or key (filename)
     */
    String getFileName() {
        if (kind == LaminUriKind.STORAGE) {
            if (!hasKey()) {
                return null
            }
            int lastSep = key.lastIndexOf(SEP)
            return lastSep >= 0 ? key.substring(lastSep + 1) : key
        }
        if (!hasSubPath()) {
            return resourceId
        }
        int lastSep = subPath.lastIndexOf(SEP)
        return lastSep >= 0 ? subPath.substring(lastSep + 1) : subPath
    }

    /**
     * Get the parent path: the sub-path or key without its last component.
     *
     * For a storage location the parent of a top-level key is the storage root itself; the
     * root has no parent.
     */
    LaminUriParser getParent() {
        if (kind == LaminUriKind.STORAGE) {
            if (!hasKey()) {
                return null
            }
            int lastSep = key.lastIndexOf(SEP)
            String parentKey = lastSep > 0 ? key.substring(0, lastSep) : null
            return new LaminUriParser(kind, owner, instance, resourceType, resourceId, null, parentKey, storageRef)
        }
        if (!hasSubPath()) {
            return null
        }
        int lastSep = subPath.lastIndexOf(SEP)
        if (lastSep <= 0) {
            return new LaminUriParser(kind, owner, instance, resourceType, resourceId, null, null, null)
        }
        return new LaminUriParser(kind, owner, instance, resourceType, resourceId, subPath.substring(0, lastSep), null, null)
    }

    /**
     * The key segments, for hierarchical Path operations. Storage URIs only.
     */
    List<String> getKeySegments() {
        return hasKey() ? (key.split(SEP) as List<String>) : ([] as List<String>)
    }

    // ==================== Percent encoding ====================

    /** Percent-encode a path, leaving the separators intact. */
    private static String encodePath(String value) {
        if (value == null) {
            return null
        }
        return value.split(SEP, -1).collect { String s -> encodeSegment(s) }.join(SEP)
    }

    /** Encode a query value, leaving the characters a query may legally carry unescaped. */
    private static String encodeQueryValue(String value) {
        return encodePath(value).replace('%3A', ':')
    }

    private static String encodeSegment(String value) {
        // URLEncoder encodes for form data: it renders a space as '+' and escapes '~'
        return URLEncoder.encode(value, 'UTF-8').replace('+', '%20')
    }

    private static String decode(String value) {
        if (value == null) {
            return null
        }
        // '+' is a literal in a path segment, so protect it from URLDecoder's form-data rules
        return URLDecoder.decode(value.replace('+', '%2B'), 'UTF-8')
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
        return kind == other.kind &&
               owner == other.owner &&
               instance == other.instance &&
               resourceType == other.resourceType &&
               resourceId == other.resourceId &&
               subPath == other.subPath &&
               key == other.key &&
               storageRef == other.storageRef
    }

    @Override
    int hashCode() {
        return Objects.hash(kind, owner, instance, resourceType, resourceId, subPath, key, storageRef)
    }
}
