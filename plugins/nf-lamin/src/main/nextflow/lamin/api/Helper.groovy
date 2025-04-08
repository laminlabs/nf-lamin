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
            println("nf-lamin> Fetched data from server: ${result.toString()}");
        } catch (ApiException e) {
            System.err.println("Exception when calling LaminApiClient#getRecord");
            System.err.println("API call failed: " + e.getMessage());
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}
