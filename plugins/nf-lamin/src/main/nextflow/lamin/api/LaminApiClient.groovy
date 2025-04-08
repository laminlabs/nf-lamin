package nextflow.lamin.api

import java.util.UUID;

import ai.lamin.lamin_api_client.ApiClient;
import ai.lamin.lamin_api_client.ApiException;
import ai.lamin.lamin_api_client.Configuration;
import ai.lamin.lamin_api_client.model.*;
import ai.lamin.lamin_api_client.api.DefaultApi;

class LaminApiClient {
    final private String apiUrl;
    final private UUID instanceId;
    final private UUID schemaId;
    final private String accessToken;
    final private DefaultApi apiInstance;

    LaminApiClient(
        String apiUrl,
        UUID instanceId,
        UUID schemaId,
        String accessToken
    ) {
        this.apiUrl = apiUrl;
        this.instanceId = instanceId;
        this.schemaId = schemaId;
        this.accessToken = accessToken;

        // Initialize the API client with the provided API URL
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath(apiUrl);
        this.apiInstance = new DefaultApi(defaultClient);
    }

    public Object getRecord(
        String moduleName,
        String modelName,
        String idOrUid,
        Integer limitToMany = 10,
        Boolean includeForeignKeys = true,
        GetRecordRequestBody getRecordRequestBody = new GetRecordRequestBody()
    ) throws ApiException {
        return this.apiInstance.getRecordInstancesInstanceIdModulesModuleNameModelNameIdOrUidPost(
            moduleName,
            modelName,
            idOrUid,
            this.instanceId,
            limitToMany,
            includeForeignKeys,
            this.schemaId,
            this.accessToken,
            getRecordRequestBody
        );
    }

    public DefaultApi getApiInstance() {
        return this.apiInstance;
    }
}