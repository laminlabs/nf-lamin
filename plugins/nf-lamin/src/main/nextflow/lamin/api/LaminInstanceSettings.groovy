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
        assert map != null : "Instance settings map is null."
        assert map.owner != null : "Instance settings - owner is null."
        assert map.owner instanceof String : "Instance settings - owner is not a string."
        assert map.name != null : "Instance settings - name is null."
        assert map.name instanceof String : "Instance settings - name is not a string."
        assert map.id != null : "Instance settings - instance ID is null."
        assert map.id instanceof String : "Instance settings - instance ID is not a string."
        assert map.schema_id != null : "Instance settings - Schema ID is null."
        assert map.schema_id instanceof String : "Instance settings - Schema ID is not a string."
        assert map.api_url != null : "Instance settings - API URL is null."
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
