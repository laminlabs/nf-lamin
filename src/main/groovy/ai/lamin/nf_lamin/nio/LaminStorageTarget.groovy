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

/**
 * A storage location of a LaminDB instance, resolved from a {@code lamin://} publish target.
 */
@CompileStatic
class LaminStorageTarget {

    /** Storage root URI, e.g. {@code s3://lamin-us-east-1/JwMEKs04D9WJ}. */
    final String storageRoot

    /** Storage UID, or null when the instance's default storage was used. */
    final String storageUid

    /** Storage record ID within the instance, or null when it was not looked up. */
    final Integer storageId

    /** Space ID within the instance that artifacts published here belong to, or null. */
    final Integer spaceId

    /** Space UID, or null when no space was selected. */
    final String spaceUid

    /** Storage backend type, e.g. {@code s3}, {@code gs}, {@code local}. */
    final String type

    /** Storage region for cloud locations. */
    final String region

    LaminStorageTarget(Map<String, Object> args) {
        this.storageRoot = args?.get('storageRoot') as String
        this.storageUid = args?.get('storageUid') as String
        this.storageId = args?.get('storageId') as Integer
        this.spaceId = args?.get('spaceId') as Integer
        this.spaceUid = args?.get('spaceUid') as String
        this.type = args?.get('type') as String
        this.region = args?.get('region') as String
    }

    /**
     * Join this storage root with a key, giving the full URI of an object.
     */
    String resolveUri(String key) {
        String root = storageRoot.endsWith(LaminUriParser.SEP)
            ? storageRoot.substring(0, storageRoot.length() - 1)
            : storageRoot
        return key ? "${root}/${key}".toString() : root
    }

    @Override
    String toString() {
        return "LaminStorageTarget(root: ${storageRoot}, uid: ${storageUid}, space: ${spaceUid ?: spaceId})"
    }
}
