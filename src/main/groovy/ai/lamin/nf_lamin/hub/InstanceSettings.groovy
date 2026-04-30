package ai.lamin.nf_lamin.hub

import java.util.UUID
import groovy.transform.CompileStatic
import ai.lamin.nf_lamin.hub.StorageSettings

@CompileStatic
class InstanceSettings {

    // Required fields
    final UUID id
    final String owner
    final String name
    final UUID schemaId
    final String apiUrl

    // Optional fields from the full get-instance-settings-v1 response
    final String lnid
    final String schemaStr
    final String lamindbVersion
    final boolean isPublic
    final boolean keepArtifactsLocal
    final boolean fineGrainedAccess
    final StorageSettings storage

    InstanceSettings(Map<String, Object> map) {
        if (!map) { throw new IllegalStateException('Instance settings map is empty.') }
        if (!map?.owner) { throw new IllegalStateException('Instance settings - owner is empty.') }
        if (!map?.name) { throw new IllegalStateException('Instance settings - name is empty.') }
        if (!map?.id) { throw new IllegalStateException('Instance settings - id is empty.') }
        if (!map?.schema_id) { throw new IllegalStateException('Instance settings - schema_id is empty.') }
        if (!map?.api_url) { throw new IllegalStateException('Instance settings - api_url is empty.') }

        Map<String, Object> storageMap = map?.get('storage') as Map<String, Object>

        this.id = UUID.fromString(map.id as String)
        this.owner = map.owner as String
        this.name = map.name as String
        this.schemaId = UUID.fromString(map.schema_id as String)
        this.apiUrl = map.api_url as String
        this.lnid = map.lnid as String
        this.schemaStr = map.schema_str as String
        this.lamindbVersion = map.lamindb_version as String
        this.isPublic = (map.get('public') as Boolean) ?: false
        this.keepArtifactsLocal = (map.keep_artifacts_local as Boolean) ?: false
        this.fineGrainedAccess = (map.fine_grained_access as Boolean) ?: false
        this.storage = storageMap ? new StorageSettings(storageMap) : null
    }

    @Override
    String toString() {
        return "InstanceSettings(id: ${id}, owner: ${owner}, name: ${name}, schemaId: ${schemaId}, apiUrl: ${apiUrl})"
    }
}
