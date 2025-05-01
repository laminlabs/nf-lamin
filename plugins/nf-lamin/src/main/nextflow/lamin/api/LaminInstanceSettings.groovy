package nextflow.lamin.api

import java.util.UUID
import groovy.transform.RecordType
import groovy.transform.CompileStatic

@CompileStatic
@RecordType
class LaminInstanceSettings {

    final UUID id
    final String owner
    final String name
    final UUID schemaId
    final String apiUrl

    static LaminInstanceSettings fromMap(Map<String, Object> map) {
        if (!map) { throw new IllegalStateException('Instance settings map is empty.') }
        if (!map?.owner) { throw new IllegalStateException('Instance settings - owner is empty.') }
        if (!map?.name) { throw new IllegalStateException('Instance settings - name is empty.') }
        if (!map?.id) { throw new IllegalStateException('Instance settings - id is empty.') }
        if (!map?.schema_id) { throw new IllegalStateException('Instance settings - schema_id is empty.') }
        if (!map?.api_url) { throw new IllegalStateException('Instance settings - api_url is empty.') }
        return new LaminInstanceSettings(
            id: UUID.fromString(map.id as String),
            owner: map.owner as String,
            name: map.name as String,
            schemaId: UUID.fromString(map.schema_id as String),
            apiUrl: map.api_url as String
        )
    }
}
