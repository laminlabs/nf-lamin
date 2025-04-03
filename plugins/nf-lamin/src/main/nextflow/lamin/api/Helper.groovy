package nextflow.lamin.api

import groovy.transform.CompileStatic
import java.util.UUID

import com.data_intuitive.lamin_api_client_java.ApiClient;
import com.data_intuitive.lamin_api_client_java.ApiException;
import com.data_intuitive.lamin_api_client_java.Configuration;
import com.data_intuitive.lamin_api_client_java.model.*;
import com.data_intuitive.lamin_api_client_java.api.DefaultApi;

@CompileStatic
class Helper {
    static void test() {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://aws.us-east-1.lamin.ai/api");

        DefaultApi apiInstance = new DefaultApi(defaultClient);
        
        UUID instanceId = UUID.fromString("037ba1e0-8d80-4f91-a902-75a47735076a");
        String moduleName = "core";
        String modelName = "artifact";
        String idOrUid = "MDG7BbeFVPvEyyUb0000";
        Integer limitToMany = 10;
        Boolean includeForeignKeys = true;
        UUID schemaId = UUID.fromString("185ae0d3-6f3f-ce2a-8122-516d30520816");
        String authorization = "Bearer ...";

        GetRecordRequestBody getRecordRequestBody = new GetRecordRequestBody();
        try {
            Object result = apiInstance.getRecordInstancesInstanceIdModulesModuleNameModelNameIdOrUidPost(moduleName, modelName, idOrUid, instanceId, limitToMany, includeForeignKeys, schemaId, authorization, getRecordRequestBody);
            println("nf-lamin> Fetched data fron server: ${result.toString()}");
        } catch (ApiException e) {
            System.err.println("Exception when calling DefaultApi#getRecordInstancesInstanceIdModulesModuleNameModelNameIdOrUidPost");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}