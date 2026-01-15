package ai.lamin.nf_lamin.instance

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import ai.lamin.lamin_api_client.ApiClient
import ai.lamin.lamin_api_client.ApiException
import ai.lamin.lamin_api_client.Configuration
import ai.lamin.lamin_api_client.model.*
import ai.lamin.lamin_api_client.api.AccountsApi
import ai.lamin.lamin_api_client.api.InstanceArtifactsApi
import ai.lamin.lamin_api_client.api.InstanceRecordsApi
import ai.lamin.lamin_api_client.api.InstanceStatisticsApi
import ai.lamin.lamin_api_client.api.InstanceTransformsApi

import ai.lamin.nf_lamin.hub.LaminHub

import nextflow.file.FileHelper

import java.nio.file.Path

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
    final protected ApiClient apiClient
    final protected AccountsApi accountsApi
    final protected InstanceArtifactsApi artifactsApi
    final protected InstanceRecordsApi recordsApi
    final protected InstanceStatisticsApi statisticsApi
    final protected InstanceTransformsApi transformsApi
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
        // Note: We create a new ApiClient per Instance to support thread safety
        // as recommended by the lamin-api-client documentation
        this.apiClient = new ApiClient()
        this.apiClient.setBasePath(this.settings.apiUrl)

        // increase the timeout for the API client to 30 seconds
        this.apiClient.setReadTimeout(30000)
        this.apiClient.setConnectTimeout(30000)
        this.apiClient.setWriteTimeout(30000)

        // Initialize the specialized API instances
        this.accountsApi = new AccountsApi(this.apiClient)
        this.artifactsApi = new InstanceArtifactsApi(this.apiClient)
        this.recordsApi = new InstanceRecordsApi(this.apiClient)
        this.statisticsApi = new InstanceStatisticsApi(this.apiClient)
        this.transformsApi = new InstanceTransformsApi(this.apiClient)

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
     * Get the API client.
     * @return the API client
     */
    ApiClient getApiClient() {
        return this.apiClient
    }

    /**
     * Fetch the instance statistics from the Lamin API.
     * @return the instance statistics
     * @throws ApiException if an error occurs while fetching the statistics
     */
    Object getInstanceStatistics() throws ApiException {
        log.trace "GET getInstanceStatistics"

        Object response = callApi { String accessToken ->
            this.statisticsApi.getInstanceStatisticsInstancesInstanceIdStatisticsGet(
                this.settings.id(),
                [],
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
            this.statisticsApi.getNonEmptyTablesInstancesInstanceIdNonEmptyTablesGet(
                this.settings.id(),
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
            this.recordsApi.getRecordInstancesInstanceIdModulesModuleNameModelNameIdOrUidPost(
                moduleName,
                modelName,
                idOrUid,
                this.settings.id(),
                limitToMany,
                includeForeignKeys,
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
            this.recordsApi.getRecordsInstancesInstanceIdModulesModuleNameModelNamePost(
                moduleName,
                modelName,
                this.settings.id(),
                limit,
                offset,
                limitToMany,
                includeForeignKeys,
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
            this.recordsApi.createRecordsInstancesInstanceIdModulesModuleNameModelNamePut(
                moduleName,
                modelName,
                this.settings.id(),
                data,
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
            this.recordsApi.updateRecordInstancesInstanceIdModulesModuleNameModelNameUidPatch(
                moduleName,
                modelName,
                uid,
                this.settings.id(),
                data,
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
            this.accountsApi.getCallerAccountAccountGet(
                //this.settings.id(),
                null,
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
     *    - kind: The kind of the transform (required)
     *    - source_code: The source code for the transform (required)
     *    - version_tag: The version_tag of the transform (optional)
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
        String kind = args.kind as String
        String sourceCode = args.source_code as String

        if (!key) { throw new IllegalStateException('Key is null. Please check the key.') }
        if (!kind) { throw new IllegalStateException('Kind is null. Please check the kind.') }
        if (!sourceCode) { throw new IllegalStateException('Source code is null. Please check the source code.') }

        // create the request body
        CreateTransformRequestBody body = new CreateTransformRequestBody(
            key: key,
            kind: kind,
            sourceCode: sourceCode
        );

        // Optional args
        for (field in ["version_tag", "reference", "reference_type", "description"]) {
            if (args.containsKey(field)) {
                body.putKwargsItem(field, args[field])
            }
        }

        // Do call
        log.trace "POST /instances/{instance_id}/transforms: ${body.toJson()}"
        Map response = callApi { String accessToken ->
            this.transformsApi.createTransformInstancesInstanceIdTransformsPost(
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
            this.artifactsApi.createArtifactInstancesInstanceIdArtifactsCreatePost(
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
     * Get an artifact by its storage path.
     * @param path The storage path of the artifact (required)
     * @return a map containing the artifact data, or null if not found
     * @throws IllegalStateException if the path is null or empty
     * @throws ApiException if an error occurs while fetching the artifact
     */
    Map getArtifactByPath(String path) {
        if (!path) { throw new IllegalStateException('Path is null or empty. Please check the path.') }

        log.trace "GET /instances/{instance_id}/artifacts/by-path?path=${path}"
        try {
            Map<String, Object> response = callApi { String accessToken ->
                this.artifactsApi.getArtifactByPathInstancesInstanceIdArtifactsByPathGet(
                    this.settings.id(),
                    path,
                    accessToken
                ) as Map<String, Object>
            }
            log.trace "Response from getArtifactByPath: ${response}"
            return response
        } catch (ApiException e) {
            // 404 is expected if artifact doesn't exist
            if (e.getCode() == 404) {
                log.trace "Artifact not found at path: ${path}"
                return null
            }
            throw e
        }
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
            this.artifactsApi.uploadArtifactInstancesInstanceIdArtifactsUploadPost(
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

    /**
     * Retrieves the storage path for an artifact from LaminDB by its UID.
     *
     * This method queries the LaminDB instance to find an artifact matching the given UID
     * (using a startswith match to support both base UIDs and versioned UIDs), retrieves
     * its storage information, and constructs the full storage path.
     *
     * If multiple artifacts match (e.g., multiple versions), the most recently updated
     * artifact is selected.
     *
     * @param uid The artifact UID (must be 16 or 20 characters long)
     * @return A Path object representing the storage location (e.g., s3://bucket/path, gs://bucket/path)
     * @throws IllegalStateException if the UID is null, empty, or has invalid length
     * @throws ApiException if no artifact is found or if the API call fails
     */
    Path getArtifactFromUid(String uid) throws ApiException {
        Map<String, Object> info = getArtifactStorageInfo(uid)
        String storageRoot = info.storageRoot as String
        String artifactKey = info.artifactKey as String

        // resolve full path using FileHelper to properly handle cloud URIs (s3://, gs://, etc.)
        Path storagePath = FileHelper.asPath(storageRoot)
        Path artifactPath = storagePath.resolve(artifactKey)
        log.info "Artifact ${uid} resolved to path: '${artifactPath.toUri()}'"

        return artifactPath
    }

    /**
     * Retrieves the storage information for an artifact from LaminDB by its UID.
     *
     * This method queries the LaminDB instance to find an artifact matching the given UID
     * (using a startswith match to support both base UIDs and versioned UIDs), retrieves
     * its storage information, and returns the storage root and artifact key separately.
     *
     * If multiple artifacts match (e.g., multiple versions), the most recently updated
     * artifact is selected.
     *
     * @param uid The artifact UID (must be 16 or 20 characters long)
     * @return A Map containing 'storageRoot' (e.g., "s3://bucket") and 'artifactKey' (e.g., ".lamindb/uid.txt")
     * @throws IllegalStateException if the UID is null, empty, or has invalid length
     * @throws ApiException if no artifact is found or if the API call fails
     */
    Map<String, Object> getArtifactStorageInfo(String uid) throws ApiException {

        if (!uid) {
            throw new IllegalStateException('UID is null or empty. Please check the UID.')
        }
        if (uid.length() != 16 && uid.length() != 20) {
            throw new IllegalStateException("UID '${uid}' is not valid. It should be 16 or 20 characters long.")
        }

        // look up artifact by uid
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
        Map artifact = records[0]
        log.debug "Found ${records.size()} artifact(s) with uid starting with '${uid}', using ${artifact.uid}"

        // get storage info
        Map storage = getStorage(artifact.storage_id as Integer)
        String storageRoot = storage.root as String

        // get artifact key
        String artifactKey = autoStorageKeyFromArtifact(artifact)

        return [
            storageRoot: storageRoot,
            artifactKey: artifactKey,
            artifactUid: artifact.uid as String
        ]
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

    // Ported from https://github.com/laminlabs/lamindb/blob/2f6be06614a7e567fc8db3ebaa0b3c370368105f/lamindb/core/storage/paths.py#L27-L47
    private String autoStorageKeyFromArtifact(Map artifact) {
        if (artifact.containsKey('_real_key') && artifact._real_key != null) {
            return artifact._real_key as String
        }
        String key = artifact.key as String
        Boolean keyIsVirtual = artifact.containsKey('_key_is_virtual') ? artifact._key_is_virtual as Boolean : false
        String uid = artifact.uid as String
        String suffix = artifact.suffix as String
        Boolean overwriteVersions = artifact.containsKey('_overwrite_versions') ? artifact._overwrite_versions as Boolean : false
        if (key == null || keyIsVirtual) {
            return autoStorageKeyFromArtifactUid(
                uid, suffix, overwriteVersions
            )
        }
        return key
    }

    private static final String AUTO_KEY_PREFIX = '.lamindb/'

    private String autoStorageKeyFromArtifactUid(
        String uid, String suffix, Boolean overwriteVersions
    ) {
        assert suffix // Suffix cannot be null.
        String uidStorage
        if (overwriteVersions) {
            uidStorage = uid.substring(0, 16)  // 16 chars, leave 4 chars for versioning
        } else {
            uidStorage = uid
        }
        String storageKey = "${AUTO_KEY_PREFIX}${uidStorage}${suffix}"
        return storageKey
    }

}
