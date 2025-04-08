package nextflow.lamin.api

import java.util.UUID;

import ai.lamin.lamin_api_client.ApiClient;
import ai.lamin.lamin_api_client.ApiException;
import ai.lamin.lamin_api_client.Configuration;
import ai.lamin.lamin_api_client.model.*;
import ai.lamin.lamin_api_client.api.DefaultApi;

class LaminApiClient {
    final protected LaminHubClient hub;
    final protected String owner;
    final protected String name;

    final protected String apiUrl;
    final protected UUID instanceId;
    final protected UUID schemaId;
    final protected DefaultApi apiInstance;

    LaminApiClient(
        LaminHubClient hub,
        String owner,
        String name
    ) {
        this.hub = hub;
        this.owner = owner;
        this.name = name;

        assert hub != null : "LaminHubClient is null. Please check the LaminHubClient instance."
        assert owner != null : "Owner is null. Please check the owner."
        assert name != null : "Name is null. Please check the name."

        def instanceSettings = hub.getInstanceSettings(this.owner, this.name);

        assert instanceSettings != null : "Instance settings are null. Please check the instance settings."
        assert instanceSettings.id != null : "Instance ID is null. Please check the instance settings."
        assert instanceSettings.schema_id != null : "Schema ID is null. Please check the instance settings."
        assert instanceSettings.api_url != null : "API URL is null. Please check the instance settings."

        this.apiUrl = instanceSettings.api_url;
        this.instanceId = UUID.fromString(instanceSettings.id);
        this.schemaId = UUID.fromString(instanceSettings.schema_id);

        // Initialize the API client with the provided API URL
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath(apiUrl);
        this.apiInstance = new DefaultApi(defaultClient);
    }

    String getBearerToken() {
        return "Bearer " + this.hub.getAccessToken();
    }

    /**
     * Get a record from the Lamin API
     * @param moduleName the module name
     * @param modelName the model name
     * @param idOrUid the ID or UID of the record
     * @param limitToMany the limit to many
     * @param includeForeignKeys whether to include foreign keys
     * @param getRecordRequestBody the request body
     * @return the record
     */
    public Object getRecord(
        String moduleName,
        String modelName,
        String idOrUid,
        Integer limitToMany = 10,
        Boolean includeForeignKeys = true,
        GetRecordRequestBody getRecordRequestBody = new GetRecordRequestBody()
    ) throws ApiException {
        // TODO: refetch accessToken if expired
        String accessToken = getBearerToken();

        return this.apiInstance.getRecordInstancesInstanceIdModulesModuleNameModelNameIdOrUidPost(
            moduleName,
            modelName,
            idOrUid,
            this.instanceId,
            limitToMany,
            includeForeignKeys,
            this.schemaId,
            accessToken,
            getRecordRequestBody
        );
    }

    /**
     * Get the Lamin API instance
     * @return the Lamin API instance
     */
    public DefaultApi getApiInstance() {
        return this.apiInstance;
    }
}