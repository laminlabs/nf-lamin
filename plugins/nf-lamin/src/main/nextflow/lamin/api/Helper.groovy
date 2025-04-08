package nextflow.lamin.api

import groovy.transform.CompileStatic
import java.util.UUID

import ai.lamin.lamin_api_client.ApiException;
import ai.lamin.lamin_api_client.model.GetRecordRequestBody;

import nextflow.lamin.api.LaminApiClient;

@CompileStatic
class Helper {
    static void test() {
        LaminApiClient client = new LaminApiClient(
            "https://aws.us-east-1.lamin.ai/api",
            UUID.fromString("037ba1e0-8d80-4f91-a902-75a47735076a"),
            UUID.fromString("185ae0d3-6f3f-ce2a-8122-516d30520816"),
            "Bearer ..."
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