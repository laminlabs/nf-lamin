/*
 * Copyright (c) 2013-2024, Lamin Labs GmbH.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package ai.lamin.nf_lamin.instance

import groovy.transform.CompileStatic

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Thrown when the Lamin API backend reports a {@code FileNotFoundError} while
 * creating an artifact. This almost always means the artifact's storage bucket
 * is not accessible via Lamin Hub for the current user, rather than the file
 * truly being absent or a transient server fault.
 *
 * <p>The exception only carries the offending bucket/path it could parse from
 * the API response. The user handle is intentionally left out here: the caller
 * (e.g. {@code LaminRunManager}) already knows the connected account and fills
 * it in when rendering the message, avoiding an extra account lookup from deep
 * inside the API client.</p>
 */
@CompileStatic
class StorageAccessException extends RuntimeException {

    /** Storage bucket the API could not access, or null if it could not be parsed. */
    final String bucket
    /** Full object path the API could not read, or null if it could not be parsed. */
    final String path

    private static final Pattern STORAGE_PATH = Pattern.compile(/(?:s3|gs|az):\/\/([^\/"\\\s]+)[^"\\\s]*/)

    StorageAccessException(String bucket, String path, Throwable cause) {
        super(buildMessage(bucket, path, null), cause)
        this.bucket = bucket
        this.path = path
    }

    /**
     * Build a {@code StorageAccessException} from an API response body, parsing
     * the offending bucket/path out of it (best effort).
     */
    static StorageAccessException fromResponseBody(String responseBody, Throwable cause) {
        Matcher m = STORAGE_PATH.matcher(responseBody ?: '')
        String path = m.find() ? m.group(0) : null
        String bucket = path ? m.group(1) : null
        return new StorageAccessException(bucket, path, cause)
    }

    /** Render the user-facing message, naming the given user handle if known. */
    String describe(String handle) {
        return buildMessage(this.bucket, this.path, handle)
    }

    private static String buildMessage(String bucket, String path, String handle) {
        String who = handle ? "user '${handle}'" : 'the current user'
        StringBuilder msg = new StringBuilder()
        msg << "It seems that ${who} does not have access to "
        msg << (bucket ? "the storage bucket '${bucket}'" : 'this storage bucket')
        msg << ' via Lamin Hub'
        if (path) {
            msg << " (the Lamin API could not read '${path}')"
        }
        msg << '. Please check that the appropriate storage location has been created'
        msg << " in Lamin Hub and that ${who} has access to it."
        return msg.toString()
    }
}
