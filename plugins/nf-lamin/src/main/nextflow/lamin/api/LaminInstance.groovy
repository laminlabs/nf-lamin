package nextflow.lamin.api

import groovy.transform.CompileStatic

import ai.lamin.lamin_api_client.ApiClient
import ai.lamin.lamin_api_client.ApiException
import ai.lamin.lamin_api_client.Configuration
import ai.lamin.lamin_api_client.model.*
import ai.lamin.lamin_api_client.api.DefaultApi

import nextflow.lamin.hub.LaminHub

/**
 * Represents a Lamin instance.
 * This class is responsible for interacting with the Lamin API.
 *
 * @param hub The LaminHub instance.
 * @param owner The owner of the instance.
 * @param name The name of the instance.
 * @throws IllegalStateException if any of the parameters are null or invalid.
 */
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
        if (!hub) { throw new IllegalStateException('LaminHub is null. Please check the LaminHub instance.') }
        if (!owner) { throw new IllegalStateException('Owner is null. Please check the owner.') }
        if (!name) { throw new IllegalStateException('Name is null. Please check the name.') }

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

    /**
     * Fetch the instance statistics from the Lamin API.
     * @return the instance statistics
     * @throws ApiException if an error occurs while fetching the statistics
     */
    Object getInstanceStatistics() throws ApiException {
        return callApi { String accessToken ->
            this.apiInstance.getInstanceStatisticsInstancesInstanceIdStatisticsGet(
                this.settings.id(),
                [],
                this.settings.schemaId(),
                accessToken
            )
        }
    }

    /**
     * Get a record from the Lamin API
     * @param args A map containing the following keys:
     *    - moduleName: The name of the module (required)
     *    - modelName: The name of the model (required)
     *    - idOrUid: The ID or UID of the record (required)
     *    - limitToMany: The limit for many records (optional, default: 10)
     *    - includeForeignKeys: Whether to include foreign keys in the response (optional, default: false)
     *    - select: A list of fields to select (optional)
     * @return a map containing the record data
     * @throws IllegalStateException if any of the required arguments are null
     * @throws ApiException if an error occurs while fetching the record
     */
    Map getRecord(Map args) throws ApiException {
        // required args
        String moduleName = args.moduleName as String
        String modelName = args.modelName as String
        String idOrUid = args.idOrUid as String

        if (!moduleName) { throw new IllegalStateException('Module name is null. Please check the module name.') }
        if (!modelName) { throw new IllegalStateException('Model name is null. Please check the model name.') }
        if (!idOrUid) { throw new IllegalStateException('ID or UID is null. Please check the ID or UID.') }

        // Optional arguments
        Integer limitToMany = args.get('limitToMany', 10) as Integer
        Boolean includeForeignKeys = args.get('includeForeignKeys', false) as Boolean
        List<String> select = args.get('select', null) as List<String>

        // Create the request body
        GetRecordRequestBody body = new GetRecordRequestBody(
            select: select
        )

        // Do call
        return callApi { String accessToken ->
            this.apiInstance.getRecordInstancesInstanceIdModulesModuleNameModelNameIdOrUidPost(
                moduleName,
                modelName,
                idOrUid,
                this.settings.id(),
                limitToMany,
                includeForeignKeys,
                this.settings.schemaId(),
                accessToken,
                body
            ) as Map
        }
    }

    /**
     * Get records from the Lamin API
     * @param args A map containing the following keys:
     *    - moduleName: The name of the module (required)
     *    - modelName: The name of the model (required)
     *    - limit: The limit for the number of records (optional, default: 50)
     *    - offset: The offset for pagination (optional, default: 0)
     *    - limitToMany: The limit for many records (optional, default: 10)
     *    - includeForeignKeys: Whether to include foreign keys in the response (optional, default: false)
     *    - search: The search term (optional)
     *    - orderBy: The order by clause (optional)
     *    - filter: The filter clause (optional)
     *    - select: A list of fields to select (optional)
     * @return a list of maps containing the record data
     * @throws IllegalStateException if any of the required arguments are null
     * @throws ApiException if an error occurs while fetching the records
     */
    List<Map> getRecords(
        Map args
    ) throws ApiException {
        // required args
        String moduleName = args.moduleName as String
        String modelName = args.modelName as String
        if (!moduleName) { throw new IllegalStateException('Module name is null. Please check the module name.') }
        if (!modelName) { throw new IllegalStateException('Model name is null. Please check the model name.') }

        // Optional arguments
        Integer limit = args.get('limit', 50) as Integer
        Integer offset = args.get('offset', 0) as Integer
        Integer limitToMany = args.get('limitToMany', 10) as Integer
        Boolean includeForeignKeys = args.get('includeForeignKeys', false) as Boolean
        String search = args.get('search', null) as String
        List<OrderByColumn> orderBy = args.get('orderBy', null) as List<OrderByColumn>
        Map<String, Object> filter = args.get('filter', null) as Map<String, Object>
        List<String> select = args.get('select', null) as List<String>
        
        // Create the request body
        GetRecordsRequestBody body = new GetRecordsRequestBody(
            select: select,
            filter: filter,
            orderBy: orderBy,
            search: search
        )

        // Do call
        return callApi { String accessToken ->
            this.apiInstance.getRecordsInstancesInstanceIdModulesModuleNameModelNamePost(
                moduleName,
                modelName,
                this.settings.id(),
                limit,
                offset,
                limitToMany,
                includeForeignKeys,
                this.settings.schemaId(),
                accessToken,
                body
            ) as List<Map>
        }
    }

    /**
     * Create a record in the Lamin API
     * @param args A map containing the following keys:
     *    - moduleName: The name of the module (required)
     *    - modelName: The name of the model (required)
     *    - data: The data to create the record (required)
     * @return a map containing the created record data
     * @throws IllegalStateException if any of the required arguments are null
     * @throws ApiException if an error occurs while creating the record
     */
    Map createRecord(
        Map args
    ) throws ApiException {
        // required args
        String moduleName = args.moduleName as String
        String modelName = args.modelName as String
        if (!moduleName) { throw new IllegalStateException('Module name is null. Please check the module name.') }
        if (!modelName) { throw new IllegalStateException('Model name is null. Please check the model name.') }

        // Optional arguments
        // drop moduleName and modelName from body
        Map data = args.get('data', null) as Map

        // Do call
        return callApi { String accessToken ->
            this.apiInstance.createRecordInstancesInstanceIdModulesModuleNameModelNamePut(
                moduleName,
                modelName,
                this.settings.id(),
                data,
                this.settings.schemaId(),
                accessToken
            ) as Map
        }
    }

    // ------------------- PRIVATE METHODS -------------------
    /**
     * Get the bearer token for authentication.
     * @return the bearer token
     */
    protected String getBearerToken() {
        return 'Bearer ' + this.hub.getAccessToken()
    }

    protected <T> T callApi(Closure<T> closure) {
        String accessToken = getBearerToken()
        try {
            return closure.call(accessToken)
        } catch (ApiException e) {
            if (e.code == 401) {
                // Token expired, refresh it and try again
                this.hub.refreshAccessToken()
                accessToken = getBearerToken()
                return closure.call(accessToken)
            }
            throw e
        }
    }
}
