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

package ai.lamin.nf_lamin.hub

import groovy.transform.CompileStatic

/**
 * Typed wrapper for the {@code storage} sub-object of the {@code get-instance-settings-v1}
 * LaminHub response.
 *
 * <p>Example payload:
 * <pre>
 * {
 *   "id": "90541d56-0ee5-4757-b93a-8afa8ace1bd1",
 *   "lnid": "YmV3ZoHvAAAA",
 *   "root": "s3://lamindata",
 *   "type": "s3",
 *   "public": true,
 *   "region": "us-east-1",
 *   "space_id": null,
 *   "created_at": "2023-02-10T02:49:33.833253+00:00",
 *   "created_by": "29cff183-c34d-445f-b6cf-31fb3b566158",
 *   "is_default": true,
 *   "updated_at": null,
 *   "description": null,
 *   "instance_id": "037ba1e0-8d80-4f91-a902-75a47735076a",
 *   "aws_account_id": null
 * }
 * </pre>
 */
@CompileStatic
class StorageSettings {

    final String id
    final String lnid
    /** Storage root URI, e.g. {@code s3://lamindata}. */
    final String root
    /** Storage backend type, e.g. {@code s3}, {@code gcs}. */
    final String type
    final boolean isPublic
    final String region
    final boolean isDefault
    final String instanceId

    // Optional / nullable fields
    final String spaceId
    final String createdAt
    final String createdBy
    final String updatedAt
    final String description
    final String awsAccountId

    StorageSettings(Map<String, Object> raw) {
        this.id = raw?.get('id') as String
        this.lnid = raw?.get('lnid') as String
        this.root = raw?.get('root') as String
        this.type = raw?.get('type') as String
        this.isPublic = (raw?.get('public') as Boolean) ?: false
        this.region = raw?.get('region') as String
        this.isDefault = (raw?.get('is_default') as Boolean) ?: false
        this.instanceId = raw?.get('instance_id') as String
        this.spaceId = raw?.get('space_id') as String
        this.createdAt = raw?.get('created_at') as String
        this.createdBy = raw?.get('created_by') as String
        this.updatedAt = raw?.get('updated_at') as String
        this.description = raw?.get('description') as String
        this.awsAccountId = raw?.get('aws_account_id') as String
    }

    @Override
    String toString() {
        return "StorageSettings(id: ${id}, root: ${root}, type: ${type}, region: ${region})"
    }
}
