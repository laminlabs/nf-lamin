package nextflow.lamin.api

import java.util.UUID
import groovy.transform.CompileStatic

@CompileStatic
record LaminInstanceSettings(
    UUID id,
    String owner,
    String name,
    UUID schemaId,
    String apiUrl
) {
    static LaminInstanceSettings fromMap(Map<String, Object> map) {
        if (!map) throw new IllegalStateException("Instance settings map is null.")
        if (!map.owner) throw new IllegalStateException("Instance settings - owner is null.")
        assert map.owner instanceof String : "Instance settings - owner is not a string."
        if (!map.name) throw new IllegalStateException("Instance settings - name is null.")
        assert map.name instanceof String : "Instance settings - name is not a string."
        if (!map.id) throw new IllegalStateException("Instance settings - instance ID is null.")
        assert map.id instanceof String : "Instance settings - instance ID is not a string."
        if (!map.schema_id) throw new IllegalStateException("Instance settings - Schema ID is null.")
        assert map.schema_id instanceof String : "Instance settings - Schema ID is not a string."
        if (!map.api_url) throw new IllegalStateException("Instance settings - API URL is null.")
        assert map.api_url instanceof String : "Instance settings - API URL is not a string."
        assert map.api_url.startsWith("http") : "Instance settings - API URL is not a valid URL."
        return new LaminInstanceSettings(
            id: UUID.fromString(map.id as String),
            owner: map.owner as String,
            name: map.name as String,
            schemaId: UUID.fromString(map.schema_id as String),
            apiUrl: map.api_url as String
        )
    }
}
