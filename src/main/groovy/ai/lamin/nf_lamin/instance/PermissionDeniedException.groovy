/*
 * Copyright (c) 2013-2024, Lamin Labs GmbH.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package ai.lamin.nf_lamin.instance

import groovy.transform.CompileStatic

/**
 * Thrown when the Lamin API denies an operation with HTTP 403. A 403 always means
 * the authenticated user lacks permission for whatever was attempted -- e.g. no
 * write access to the instance/space, or the target storage location is not
 * registered/accessible via Lamin Hub.
 *
 * <p>The exception captures the operation and its arguments. The user handle is
 * intentionally left out here: the caller (e.g. {@code LaminRunManager}) already
 * knows the connected account and fills it in when rendering the message, avoiding
 * an extra account lookup from deep inside the API client.</p>
 */
@CompileStatic
class PermissionDeniedException extends RuntimeException {

    /** The attempted operation, e.g. "POST createArtifact". */
    final String operation
    /** A description of the data the operation acted on, or null. */
    final String data

    PermissionDeniedException(String operation, String data, Throwable cause) {
        super(buildMessage(operation, data, null), cause)
        this.operation = operation
        this.data = data
    }

    /** Render the user-facing message, naming the given user handle if known. */
    String describe(String handle) {
        return buildMessage(this.operation, this.data, handle)
    }

    private static String buildMessage(String operation, String data, String handle) {
        String who = handle ? "user '${handle}'" : 'the current user'
        StringBuilder msg = new StringBuilder()
        msg << "It seems that ${who} does not have permission to perform "
        msg << (operation ? "'${operation}'" : 'this operation')
        if (data) {
            msg << " with data: ${data}"
        }
        msg << ". Please check that ${who} has the necessary access in Lamin Hub"
        msg << ' (e.g. write access to the space, or that the target storage location'
        msg << ' is registered and accessible).'
        return msg.toString()
    }
}
