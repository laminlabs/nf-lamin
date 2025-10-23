package ai.lamin.nf_lamin.instance

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import ai.lamin.lamin_api_client.ApiClient
import ai.lamin.lamin_api_client.ApiException
import ai.lamin.lamin_api_client.Configuration
import ai.lamin.lamin_api_client.model.*
import ai.lamin.lamin_api_client.api.DefaultApi

import ai.lamin.nf_lamin.hub.LaminHub

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Represents a Lamin instance.
 * This class is responsible for interacting with the Lamin API.
 *
 * @param hub The LaminHub instance.
 * @param owner The owner of the instance.
 * @param name The name of the instance.
 * @throws IllegalStateException if any of the parameters are null or invalid.
 */
@Slf4j
@CompileStatic
class Instance {

    final protected LaminHub hub
    final protected InstanceSettings settings
    final protected DefaultApi apiInstance
    final protected Integer maxRetries
    final protected Integer retryDelay

    /**
     * Constructor for the Instance class.
     *
     * @param hub The LaminHub instance.
     * @param settings The settings for the instance.
     * @throws IllegalStateException if any of the parameters are null or invalid.
     */
    Instance(
        LaminHub hub,
        InstanceSettings settings,
        Integer maxRetries,
        Integer retryDelay
    ) {
        if (!hub) { throw new IllegalStateException('LaminHub is null. Please check the LaminHub instance.') }
        if (!settings) { throw new IllegalStateException('InstanceSettings is null. Please check the InstanceSettings instance.') }

        this.hub = hub
        this.settings = settings

        // Initialize the API client with the provided API URL
        ApiClient defaultClient = Configuration.getDefaultApiClient()
        defaultClient.setBasePath(this.settings.apiUrl)
        this.apiInstance = new DefaultApi(defaultClient)

        // increase the timeout for the API client to 30 seconds
        this.apiInstance.getApiClient().setReadTimeout(30000)
        this.apiInstance.getApiClient().setConnectTimeout(30000)
        this.apiInstance.getApiClient().setWriteTimeout(30000)

        // set maxRetries and retryDelay
        this.maxRetries = maxRetries
        this.retryDelay = retryDelay
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
     * Get the LaminHub instance.
     * @return the hub
     */
    LaminHub getHub() {
        return this.hub
    }

    /**
     * Get the instance settings.
     * @return the settings
     */
    InstanceSettings getSettings() {
        return this.settings
    }

    /**
     * Get the maximum number of retries.
     * @return the max retries
     */
    Integer getMaxRetries() {
        return this.maxRetries
    }

    /**
     * Get the retry delay.
     * @return the retry delay
     */
    Integer getRetryDelay() {
        return this.retryDelay
    }

    /**
     * Get the API instance.
     * @return the API instance
     */
    DefaultApi getApiInstance() {
        return this.apiInstance
    }

    /**
     * Fetch the instance statistics from the Lamin API.
     * @return the instance statistics
     * @throws ApiException if an error occurs while fetching the statistics
     */
    Object getInstanceStatistics() throws ApiException {
        log.trace "GET getInstanceStatistics"

        Object response = callApi { String accessToken ->
            this.apiInstance.getInstanceStatisticsInstancesInstanceIdStatisticsGet(
                this.settings.id(),
                [],
                this.settings.schemaId(),
                accessToken
            )
        }
        log.trace "Response from getInstanceStatistics: ${response}"
        return response
    }

    /**
     * Get the non-empty tables from the Lamin API.
     * @return a Map containing the non-empty tables
     * @throws ApiException if an error occurs while fetching the non-empty tables
     */
    Map getNonEmptyTables() throws ApiException {
        log.trace "GET getNonEmptyTables"

        Map response = callApi { String accessToken ->
            this.apiInstance.getNonEmptyTablesInstancesInstanceIdNonEmptyTablesGet(
                this.settings.id(),
                this.settings.schemaId(),
                accessToken
            )
        } as Map

        log.trace "Response from getNonEmptyTables: ${response}"

        return response
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
        log.trace "POST getRecord: ${moduleName}.${modelName}, idOrUid=${idOrUid}"
        Map response = callApi { String accessToken ->
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
        log.trace "Response from getRecord: ${response}"
        return response
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
        log.trace "POST getRecords: ${moduleName}.${modelName}, filter=${filter}, limit=${limit}, offset=${offset}"
        List<Map> response = callApi { String accessToken ->
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
        log.trace "Response from getRecords: ${response}"
        return response
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
    Map createRecord(Map args) {
        // required args
        String moduleName = args.moduleName as String
        String modelName = args.modelName as String
        if (!moduleName) { throw new IllegalStateException('Module name is null. Please check the module name.') }
        if (!modelName) { throw new IllegalStateException('Model name is null. Please check the model name.') }

        // Optional arguments
        // drop moduleName and modelName from body
        Map data = args.get('data', null) as Map

        // Do call
        log.trace "PUT createRecord: ${moduleName}.${modelName}, data=${data}"
        List<Map> response = callApi { String accessToken ->
            this.apiInstance.createRecordsInstancesInstanceIdModulesModuleNameModelNamePut(
                moduleName,
                modelName,
                this.settings.id(),
                data,
                this.settings.schemaId(),
                accessToken
            )
        } as List<Map>
        log.trace "Response from createRecord: ${response}"
        if (response == null || response.isEmpty()) {
            throw new IllegalStateException("Failed to create record. Response is empty.")
        }
        if (response.size() > 1) {
            log.warn "Multiple records created. Returning the first one."
        }
        return response[0]
    }

    /**
     * Update a record in the Lamin API
     * @param args A map containing the following keys:
     *    - moduleName: The name of the module (required)
     *    - modelName: The name of the model (required)
     *    - uid: The UID of the record (required)
     *    - data: The data to update the record (optional)
     * @return a map containing the updated record data
     * @throws IllegalStateException if any of the required arguments are null
     * @throws ApiException if an error occurs while updating the record
     */
    Map updateRecord(Map args) {
        // required args
        String moduleName = args.moduleName as String
        String modelName = args.modelName as String
        String uid = args.uid as String

        if (!moduleName) { throw new IllegalStateException('Module name is null. Please check the module name.') }
        if (!modelName) { throw new IllegalStateException('Model name is null. Please check the model name.') }
        if (!uid) { throw new IllegalStateException('UID is null. Please check the UID.') }

        // Optional arguments
        Map data = args.get('data', null) as Map

        // Do call
        log.trace "PATCH updateRecord: ${moduleName}.${modelName}, uid=${uid}, data=${data}"
        Map response = callApi { String accessToken ->
            this.apiInstance.updateRecordInstancesInstanceIdModulesModuleNameModelNameUidPatch(
                moduleName,
                modelName,
                uid,
                this.settings.id(),
                data,
                this.settings.schemaId(),
                accessToken
            ) as Map
        }
        log.trace "Response from updateRecord: ${response}"
        return response
    }

    /**
     * Get the account information from the Lamin API.
     * @return a map containing the account information
     * @throws ApiException if an error occurs while fetching the account information
     */
    Map getAccount() {
        log.trace "GET /account"

        Map response = callApi { String accessToken ->
            this.apiInstance.getCallerAccountAccountGet(
                accessToken
            ) as Map
        }
        log.trace "Response from getAccount: ${response}"
        return response
    }

    /**
     * Create a transform in the Lamin API.
     * @param args A map containing the following
     *    - key: The key for the transform (required)
     *    - type: The type of the transform (required)
     *    - source_code: The source code for the transform (required)
     *    - version: The version of the transform (optional)
     *    - reference: The reference for the transform (optional)
     *    - reference_type: The reference type for the transform (optional)
     *    - description: The description of the transform (optional)
     * @return a map containing the created transform data
     * @throws IllegalStateException if any of the required arguments are null
     * @throws ApiException if an error occurs while creating the transform
    */
    Map createTransform(Map args) {
        // Required args
        String key = args.key as String
        String type = args.type as String
        String sourceCode = args.source_code as String

        if (!key) { throw new IllegalStateException('Key is null. Please check the key.') }
        if (!type) { throw new IllegalStateException('Type is null. Please check the type.') }
        if (!sourceCode) { throw new IllegalStateException('Source code is null. Please check the source code.') }

        // create the request body
        CreateTransformRequestBody body = new CreateTransformRequestBody(
            key: key,
            type: type,
            sourceCode: sourceCode
        );

        // Optional args
        for (field in ["version", "reference", "reference_type", "description"]) {
            if (args.containsKey(field)) {
                body.putKwargsItem(field, args[field])
            }
        }

        // Do call
        log.trace "POST /instances/{instance_id}/transforms: ${body.toJson()}"
        Map response = callApi { String accessToken ->
            this.apiInstance.createTransformInstancesInstanceIdTransformsPost(
                this.settings.id(),
                body,
                accessToken
            ) as Map
        }
        log.trace "Response from createTransform: ${response}"

        Map responseBody = response?.body as Map

        if (responseBody?.transform == null) {
            throw new IllegalStateException("Failed to create transform. Response: ${response}")
        }

        return responseBody?.transform as Map
    }

    /**
     * Create an artifact in the Lamin API.
     * @param args A map containing the following keys:
     *    - path: The path to the artifact (required)
     *    - Any other fields will be passed as kwargs to the API
     * @return a map containing the created artifact data
     * @throws IllegalStateException if the path is null or empty
     * @throws ApiException if an error occurs while creating the artifact
     */
    Map createArtifact(Map<String, Object> args) {
        // Required args
        String path = args.path as String
        if (!path) { throw new IllegalStateException('Path is null or empty. Please check the path.') }

        // Create body
        CreateArtifactRequestBody body = new CreateArtifactRequestBody(
            path: path
        )

        // Pass all other args as kwargs (excluding 'path')
        args.each { String key, Object value ->
            if (key != 'path') {
                body.putKwargsItem(key, value)
            }
        }

        // Do call
        log.trace "POST /instances/{instance_id}/artifacts/create: ${body.toJson()}"

        Map<String, Object> response = callApi { String accessToken ->
            this.apiInstance.createArtifactInstancesInstanceIdArtifactsCreatePost(
                this.settings.id(),
                body,
                accessToken
            ) as Map<String, Object>
        }

        log.trace "Response from createArtifact: ${response}"

        Map<String, Object> responseBody = response?.body as Map<String, Object>
        if (!responseBody?.artifact) {
            throw new IllegalStateException("Failed to create artifact. Response: ${response}")
        }

        return responseBody?.artifact as Map<String, Object>
    }

    /**
     * Upload an artifact to the Lamin API.
     * @param args A map containing the following keys:
     *    - file: The file to upload (required)
     *    - Any other fields will be passed as kwargs to the API
     * @return a map containing the uploaded artifact data
     * @throws IllegalStateException if the file is null or does not exist
     * @throws ApiException if an error occurs while uploading the artifact
     */
    Map uploadArtifact(Map<String, Object> args) {
        // Required args
        File file = args.file as File
        if (!file || !file.exists()) {
            throw new IllegalStateException('File is null or does not exist. Please check the file.')
        }

        // Create kwargs from all args (excluding 'file')
        Map<String, Object> kwargs = [:]
        args.each { String key, Object value ->
            if (key != 'file') {
                kwargs[key] = value
            }
        }
        String kwargsString = kwargs ? groovy.json.JsonOutput.toJson(kwargs) : '{}'

        // Do call
        log.trace "POST /instances/{instance_id}/artifacts/upload: file=${file}, kwargs=${kwargsString}"
        Map<String, Object> response = callApi { String accessToken ->
            this.apiInstance.uploadArtifactInstancesInstanceIdArtifactsUploadPost(
                this.settings.id(),
                file,
                accessToken,
                kwargsString
            ) as Map<String, Object>
        }
        log.trace "Response from uploadArtifact: ${response}"

        Map<String, Object> responseBody = response?.body as Map<String, Object>
        if (!responseBody?.artifact) {
            throw new IllegalStateException("Failed to upload artifact. Response: ${response}")
        }
        return responseBody?.artifact as Map<String, Object>
    }

    Path getArtifactUrlByUid(String uid) throws ApiException {

        if (!uid) {
            throw new IllegalStateException('UID is null or empty. Please check the UID.')
        }
        if (uid.length() != 16 && uid.length() != 20) {
            throw new IllegalStateException("UID '${uid}' is not valid. It should be 16 or 20 characters long.")
        }

    /*
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
     */
        Map args = [
            moduleName: 'core',
            modelName: 'artifact',
            limit: 1,
            offset: 0,
            includeForeignKeys: true,
            orderBy: [['field': 'updated_at', 'descending': true]],
            filter: [
                'uid': ['startswith': uid]
            ]
        ]
        List<Map> records = getRecords(args)

        if (records.size() == 0) {
            throw new ApiException("No artifact found with uid starting with '${uid}'")
        }
        log.info "Found ${records.size()} artifacts with uid starting with '${uid}'"
        Map artifact = records[0]
        log.info "Using artifact: ${artifact}"

        Map storage = getStorage(artifact.storage_id as Integer)
        log.info "Storage details: ${storage}"

        String storageRoot = storage.root as String
        String key = artifact.key as String
        log.debug "Storage root: ${storageRoot}, Artifact key: ${key}"
        
        Path combined = Paths.get(storageRoot).resolve(key)
        log.info "Constructed artifact URL: ${combined}"

        return combined
    }

    // ------------------- PRIVATE METHODS -------------------
    /**
     * Get the bearer token for authentication.
     * @return the bearer token
     */
    protected String getBearerToken() {
        return 'Bearer ' + this.hub.getAccessToken()
    }

    /**
     * Call the Lamin API with the provided closure.
     * This method handles token expiration and refreshes the token if necessary.
     *
     * @param closure The closure to call with the access token
     * @return the result of the closure call
     * @throws ApiException if an error occurs while calling the API
     */
    protected <T> T callApi(Closure<T> closure, Integer retries = 0) throws ApiException {
        String accessToken = getBearerToken()
        try {
            return closure.call(accessToken)
        } catch (ApiException e) {
            if (e.code == 401) {
                // Token expired, refresh it and try again
                this.hub.refreshAccessToken()
                accessToken = getBearerToken()
                return closure.call(accessToken)
            } else if (retries <= this.maxRetries) {
                // Retry the API call
                log.warn "API call failed with status ${e.code}. Retrying (${retries + 1}/${this.maxRetries})..."
                Thread.sleep(this.retryDelay)
                return callApi(closure, retries + 1)
            }

            throw e
        }
    }

    protected Map getStorage(Integer id) throws ApiException {
        log.trace "GET getStorage: id=${id}"

        Map response = getRecord([
            moduleName: 'core',
            modelName: 'storage',
            idOrUid: id,
            includeForeignKeys: false
        ])
        log.trace "Response from getStorage: ${response}"
        return response
    }

}
