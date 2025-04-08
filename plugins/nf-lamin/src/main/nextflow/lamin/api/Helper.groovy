package nextflow.lamin.api

import java.util.UUID

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import ai.lamin.lamin_api_client.ApiException;
import ai.lamin.lamin_api_client.model.GetRecordRequestBody;

import nextflow.lamin.api.LaminHubClient;
import nextflow.lamin.api.LaminApiClient;

@CompileStatic
@Slf4j
class Helper {
    static void test() {
        String apiKey = "..."
        
        // Fetch instance settings
        LaminHubClient hub = new LaminHubClient(apiKey)

        LaminApiClient client = new LaminApiClient(
            hub,
            "laminlabs",
            "lamindata"
        );
        
        Integer limitToMany = 10;
        Boolean includeForeignKeys = true;
        GetRecordRequestBody getRecordRequestBody = new GetRecordRequestBody();

        try {
            Object result = client.getRecord(
                // moduleName: "core",
                // modelName: "artifact",
                // idOrUid: "MDG7BbeFVPvEyyUb0000",
                // includeForeignKeys: true
                "core",
                "artifact",
                "MDG7BbeFVPvEyyUb0000",
                limitToMany,
                includeForeignKeys,
                getRecordRequestBody
            );
            log.info "nf-lamin> Fetched data from server: ${result.toString()}"
        } catch (ApiException e) {
            log.error "nf-lamin> Exception when calling LaminApiClient#getRecord"
            log.error "API call failed: " + e.getMessage()
            log.error "Status code: " + e.getCode()
            log.error "Response body: " + e.getResponseBody()
            log.error "Response headers: " + e.getResponseHeaders()
        }
    }
}