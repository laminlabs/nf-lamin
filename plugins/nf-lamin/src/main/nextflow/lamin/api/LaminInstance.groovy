package nextflow.lamin.api

import java.util.UUID

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import ai.lamin.lamin_api_client.ApiClient
import ai.lamin.lamin_api_client.ApiException
import ai.lamin.lamin_api_client.Configuration
import ai.lamin.lamin_api_client.model.*
import ai.lamin.lamin_api_client.api.DefaultApi

@CompileStatic
class LaminInstance {

    final protected LaminHub hub

    final protected LaminInstanceSettings settings
    final protected DefaultApi apiInstance

    /**
     * Constructor for the LaminInstance class.
     *
     * @param hub The LaminHub instance.
     * @param owner The owner of the instance.
     * @param name The name of the instance.
     * @throws IllegalStateException if any of the parameters are null or invalid.
     */
    LaminInstance(
        LaminHub hub,
        String owner,
        String name
    ) {
        if (!hub) throw new IllegalStateException('LaminHub is null. Please check the LaminHub instance.')
        if (!owner) throw new IllegalStateException('Owner is null. Please check the owner.')
        if (!name) throw new IllegalStateException('Name is null. Please check the name.')

        this.hub = hub
        this.settings = hub.getInstanceSettings(owner, name)

        // Initialize the API client with the provided API URL
        ApiClient defaultClient = Configuration.getDefaultApiClient()
        defaultClient.setBasePath(this.settings.apiUrl)
        this.apiInstance = new DefaultApi(defaultClient)
    }

    /**
     * The owner of the instance.
     * @return the owner
     */
    String getOwner() {
        return this.settings.owner()
    }

    /**
     * The name of the instance.
     * @return the name
     */
    String getName() {
        return this.settings.name()
    }

    protected String getBearerToken() {
        return 'Bearer ' + this.hub.getAccessToken()
    }

    /**
     * Fetch the instance statistics from the Lamin API.
     * @return the instance statistics
     * @throws ApiException if an error occurs while fetching the statistics
     */
    Object getInstanceStatistics() throws ApiException {
        String accessToken = getBearerToken()

        return this.apiInstance.getInstanceStatisticsInstancesInstanceIdStatisticsGet(
            this.settings.id(),
            [],
            this.settings.schemaId(),
            accessToken
        )
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
     * @throws ApiException if an error occurs while fetching the record
     */
    Object getRecord(
        String moduleName,
        String modelName,
        String idOrUid,
        Integer limitToMany = 10,
        Boolean includeForeignKeys = true,
        GetRecordRequestBody getRecordRequestBody = new GetRecordRequestBody()
    ) throws ApiException {
        // TODO: refetch accessToken if expired
        String accessToken = getBearerToken()

        return this.apiInstance.getRecordInstancesInstanceIdModulesModuleNameModelNameIdOrUidPost(
            moduleName,
            modelName,
            idOrUid,
            this.settings.id(),
            limitToMany,
            includeForeignKeys,
            this.settings.schemaId(),
            accessToken,
            getRecordRequestBody
        )
    }

}
