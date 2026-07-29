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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

import ai.lamin.nf_lamin.hub.InstanceSettings
import ai.lamin.nf_lamin.hub.StorageSettings
import ai.lamin.nf_lamin.instance.Instance

/**
 * Resolves the storage location that a {@code lamin://} publish target points at.
 *
 * The space and storage of a target are looked up once and cached, because publishing runs
 * on a thread pool and would otherwise hit the API once per published file.
 */
@Slf4j
@CompileStatic
class LaminStorageResolver {

    private final Map<String, LaminStorageTarget> cache = new ConcurrentHashMap<String, LaminStorageTarget>()
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<String, ReentrantLock>()

    /**
     * Resolve a publish target to its storage location.
     *
     * @param instance The instance client for the target's instance
     * @param uri The parsed publish target (must be of kind STORAGE)
     * @return The resolved storage location
     * @throws IllegalArgumentException if the space or storage does not exist, if they
     *         disagree, or if the storage is not managed by this instance
     */
    LaminStorageTarget resolve(Instance instance, LaminUriParser uri) {
        if (!uri.storage) {
            throw new IllegalArgumentException("Not a storage URI: ${uri}")
        }

        String cacheKey = "${uri.instanceSlug}|${uri.spaceRef}|${uri.storageReference}"
        LaminStorageTarget cached = cache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        ReentrantLock lock = locks.computeIfAbsent(cacheKey) { new ReentrantLock() }
        lock.lock()
        try {
            LaminStorageTarget target = cache.get(cacheKey)
            if (target == null) {
                target = resolveTarget(instance, uri)
                cache.put(cacheKey, target)
                log.debug "Resolved ${uri} to ${target}"
            }
            return target
        }
        finally {
            lock.unlock()
        }
    }

    /**
     * Drop all cached targets.
     */
    void clear() {
        cache.clear()
        locks.clear()
    }

    private LaminStorageTarget resolveTarget(Instance instance, LaminUriParser uri) {
        InstanceSettings settings = instance.settings
        String slug = uri.instanceSlug

        Map<String, Object> space = resolveSpace(instance, uri.spaceRef, slug)
        Integer spaceId = intOf(space?.get('id'))

        Map<String, Object> storage = resolveStorage(instance, uri.storageReference, spaceId, uri.spaceRef, slug)

        if (storage == null) {
            // No selector at all: use the instance's default storage. Its space is only known
            // to the hub as a UUID, so the artifact's space is left to the plugin config.
            StorageSettings defaultStorage = settings.storage
            if (defaultStorage?.root == null) {
                throw new IllegalArgumentException(
                    "Instance '${slug}' has no default storage location; select one with '?storage=<uid>'"
                )
            }
            return new LaminStorageTarget(
                storageRoot: defaultStorage.root,
                storageUid: defaultStorage.lnid,
                type: defaultStorage.type,
                region: defaultStorage.region
            )
        }

        String root = storage.get('root') as String
        String storageUid = storage.get('uid') as String
        Integer storageSpaceId = intOf(storage.get('space_id'))

        if (spaceId != null && storageSpaceId != null && storageSpaceId != spaceId) {
            throw new IllegalArgumentException(
                "Storage '${storageUid}' belongs to space ${storageSpaceId}, but '${uri}' selects space " +
                "'${uri.spaceRef}' (${spaceId}). LaminDB requires them to match."
            )
        }

        String owningInstance = storage.get('instance_uid') as String
        if (owningInstance != null && settings.lnid != null && owningInstance != settings.lnid) {
            throw new IllegalArgumentException(
                "Storage '${storageUid}' (${root}) is read-only in instance '${slug}': it is managed by " +
                "instance '${owningInstance}'. Publish to a storage location managed by '${slug}'."
            )
        }

        return new LaminStorageTarget(
            storageRoot: root,
            storageUid: storageUid,
            storageId: intOf(storage.get('id')),
            spaceId: spaceId ?: storageSpaceId,
            spaceUid: space?.get('uid') as String,
            type: storage.get('type') as String,
            region: storage.get('region') as String
        )
    }

    private Map<String, Object> resolveSpace(Instance instance, String spaceRef, String slug) {
        if (!spaceRef) {
            return null
        }
        Map<String, Object> space = instance.getRecord(
            moduleName: 'core',
            modelName: 'space',
            idOrUid: spaceRef
        ) as Map<String, Object>
        if (space == null) {
            throw new IllegalArgumentException("No space with UID '${spaceRef}' in instance '${slug}'")
        }
        return space
    }

    private Map<String, Object> resolveStorage(Instance instance, String storageRef, Integer spaceId,
                                               String spaceRef, String slug) {
        if (storageRef) {
            Map<String, Object> storage = instance.getRecord(
                moduleName: 'core',
                modelName: 'storage',
                idOrUid: storageRef
            ) as Map<String, Object>
            if (storage == null) {
                throw new IllegalArgumentException("No storage with UID '${storageRef}' in instance '${slug}'")
            }
            return storage
        }

        if (spaceId == null) {
            return null
        }

        // Same choice LaminDB makes when an artifact is created with a space but no storage:
        // the instance's lowest-id storage location in that space
        List<Map> records = instance.getRecords(
            moduleName: 'core',
            modelName: 'storage',
            limit: 1,
            filter: [
                and: [
                    [space_id: [eq: spaceId]],
                    [instance_uid: [eq: instance.settings.lnid]]
                ]
            ],
            orderBy: [[field: 'id', descending: false]]
        )
        if (!records) {
            throw new IllegalArgumentException(
                "No storage location found for space '${spaceRef}' in instance '${slug}'. " +
                "Create one with ln.Storage(root='...', space=space).save(), or move an existing " +
                "storage location into the space."
            )
        }
        return records[0] as Map<String, Object>
    }

    private static Integer intOf(Object value) {
        return value == null ? null : ((Number) value).intValue()
    }
}
