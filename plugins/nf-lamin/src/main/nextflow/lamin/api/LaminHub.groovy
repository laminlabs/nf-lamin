package nextflow.lamin.api

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovy.json.JsonSlurper

/**
 * Groovy client for interacting with specific Lamin Hub API endpoints.
 * Handles fetching JWT and instance settings.
 */
@Slf4j
@CompileStatic
class LaminHub {
    // --- Constants ---

    // Note: these values could be parameterized similar to
    // https://github.com/laminlabs/lamindb-setup/blob/30f1a4dbbdaa37ab31333d0cc7444730eceb4e12/lamindb_setup/core/_hub_client.py#L32-L60
    // Base URL for the Lamin Hub API
    private static final String BASE_URL = "https://hub.lamin.ai/functions/v1"
    // The anonymous key for production access
    private static final String PROD_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxhZXNhdW1tZHlkbGxwcGdmY2h1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2NTY4NDA1NTEsImV4cCI6MTk3MjQxNjU1MX0.WUeCRiun0ExUxKIv5-CtjF6878H8u26t0JmCWx3_2-c"
    // Connection timeout for the connection
    private static final int CONNECT_TIMEOUT_MS = 5000
    // Read timeout for the connection
    private static final int READ_TIMEOUT_MS = 15000

    // --- Instance Fields ---
    // The API key for the user
    private final String apiKey
    // The JWT access token fetched from the API
    private String accessToken

    /**
     * Constructor.
     * @param apiKey The user's Lamin Hub API Key.
     */
    LaminHub(String apiKey) {
        if (!apiKey?.trim()) {
            throw new IllegalArgumentException("API Key cannot be null or empty.")
        }
        this.apiKey = apiKey
        this.accessToken = null
    }

    /**
     * Fetches a JWT access token using the API Key.
     *
     * @return The fetched JWT access token.
     * @throws RuntimeException If the API call fails or response parsing fails.
     */
    String fetchAccessToken() {
        String url = "${BASE_URL}/get-jwt-v1"
        String payload = """{"api_key": "${this.apiKey}"}"""
        String currentMethod = "fetchAccessToken()"

        // Use the PROD_ANON_KEY for this specific authorization step
        String responseJson = makePostRequest(url, payload, PROD_ANON_KEY, false, currentMethod)

        try {
            def responseMap = parseJson(responseJson, currentMethod)
            if (responseMap instanceof Map && responseMap.containsKey('accessToken')) {
                def accessToken = responseMap.accessToken as String
                if (accessToken == null || accessToken.trim().isEmpty()) {
                    throw new RuntimeException("Received empty accessToken from ${url}")
                }
                return accessToken
            } else {
                throw new RuntimeException("Failed to parse 'accessToken' from response. URL: ${url}. Response: ${responseJson}")
            }
        } catch (Exception e) {
            throw new RuntimeException("Error parsing JWT response from ${url}. Response: ${responseJson}", e)
        }
    }

    /**
     * Retrieves the JWT access token, fetching it if not already available.
     *
     * @return The JWT access token.
     * @throws RuntimeException If the API call fails or the token cannot be fetched.
     */
    String getAccessToken() {
        if (this.accessToken == null) {
            log.debug("Fetching access token...")
            updateAccessToken()
        }
        return this.accessToken
    }

    /**
     * Refreshes the JWT access token.
     * This method is useful for renewing the token if it has expired or is invalid.
     *
     * @throws RuntimeException If the API call fails or the token cannot be refreshed.
     */
    void refreshAccessToken() {
        log.debug("Refreshing access token...")
        updateAccessToken()
    }

    /**
     * Fetches instance settings for a given owner and name.
     * This method requires a valid JWT access token.
     *
     * @param owner The owner of the instance.
     * @param name The name of the instance.
     * @return The JSON string containing instance settings.
     * @throws IllegalStateException If JWT accessToken has not been fetched yet.
     * @throws RuntimeException If the API call fails.
     */
    LaminInstanceSettings getInstanceSettings(String owner, String name) {
        String accessToken = getAccessToken()

        String url = "${BASE_URL}/get-instance-settings-v1"
        // Use triple-quoted string with interpolation for readability
        String payload = """
        {
            "owner": "${owner}",
            "name": "${name}"
        }
        """.stripIndent()
        String currentMethod = "getInstanceSettings(owner='${owner}', name='${name}')"

        // Use the fetched accessToken for authorization here
        String instanceSettingsJson = makePostRequest(url, payload, accessToken, true, currentMethod)

        Map instanceSettingsMap = parseJson(instanceSettingsJson, currentMethod)

        return LaminInstanceSettings.fromMap(instanceSettingsMap)
    }

    

    // --- Private Helper Methods ---

    /**
     * Performs the actual HTTP POST request.
     *
     * @param requestUrl The full URL endpoint.
     * @param jsonPayload The JSON body as a String.
     * @param bearerToken The Bearer token for the Authorization header.
     * @return The response body as a String on success (HTTP 200).
     * @throws RuntimeException For connection errors or non-200 responses.
     */
    private String makePostRequest(String requestUrl, String jsonPayload, String bearerToken, boolean allowRetry, String callingMethod) {
        HttpURLConnection connection = null
        String responseBody = null
        int responseCode = -1

        log.trace "Making POST request to ${requestUrl} for method ${callingMethod}. Retry allowed: ${allowRetry}"

        try {
            URL url = new URL(requestUrl)
            connection = (HttpURLConnection) url.openConnection()

            // --- Configuration ---
            connection.setRequestMethod("POST")
            connection.setRequestProperty("Authorization", "Bearer ${bearerToken}")
            connection.setRequestProperty("Content-Type", "application/json; utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setDoOutput(true)
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS)
            connection.setReadTimeout(READ_TIMEOUT_MS)
            connection.setInstanceFollowRedirects(false)

            // --- Send Request Body ---
            connection.outputStream.withWriter(StandardCharsets.UTF_8.name()) { writer ->
                writer.write(jsonPayload)
            }

            // --- Get Response ---
            responseCode = connection.getResponseCode()

            if (responseCode == HttpURLConnection.HTTP_OK) { // Success (200)
                connection.inputStream.withReader(StandardCharsets.UTF_8.name()) { reader ->
                    responseBody = reader.getText() // Groovy helper reads entire stream
                }
                if (responseBody == null || responseBody.trim().isEmpty()) {
                    // Handle cases where API returns 200 OK but empty body unexpectedly
                    System.err.println("Warning: Received HTTP 200 but empty response body from ${requestUrl}")
                    // Depending on API contract, you might throw an error or return empty/null
                    // Returning empty string here for now.
                    return ""
                }
                return responseBody

            } else if (allowRetry && (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN)) {
                // --- Authorization Error & Retry Allowed ---
                log.warn "Received HTTP ${responseCode} (${connection.getResponseMessage()}) from ${requestUrl} for ${callingMethod}. Attempting to refresh access token..."

                // Disconnect the failed connection before attempting refresh/retry
                if (connection != null) { connection.disconnect(); connection = null }

                try {
                    // Refresh the token (this updates this.accessToken internally via getJwt)
                    refreshAccessToken()
                    String newAccessToken = getAccessToken()

                    log.debug "Retrying request to ${requestUrl} for ${callingMethod} with new token..."
                    // !!! Recursive Call: Retry the request ONCE with the NEW token and allowRetry=false !!!
                    // Pass the updated this.accessToken stored in the instance
                    return makePostRequest(requestUrl, jsonPayload, newAccessToken, false, callingMethod + " [Retry]")

                } catch (Exception refreshOrRetryException) {
                    // Handle failure during refresh or the retry attempt
                    throw new RuntimeException("Failed during token refresh or retry attempt for ${requestUrl} (Initial code: ${responseCode}). Error: ${refreshOrRetryException.getMessage()}", refreshOrRetryException)
                }
            } else {
                // --- Other Error or Retry Not Allowed ---
                String errorBody = null
                InputStream errorStream = connection.errorStream
                if (errorStream != null) {
                    // Add try-catch around reading error stream
                    try {
                        errorStream.withReader(StandardCharsets.UTF_8.name()) { reader ->
                            errorBody = reader.getText()
                        }
                    } catch (IOException readEx) {
                        errorBody = "(Failed to read error stream: ${readEx.getMessage()})"
                    }
                }

                // Construct detailed error message
                String errorMessage = "HTTP Error: ${responseCode} ${connection.getResponseMessage()}. " +
                    "URL: ${requestUrl}. Method: ${callingMethod}. " +
                    "Payload: ${jsonPayload}. " +
                    "Retry Allowed: ${allowRetry}. " +
                    "Response: ${errorBody ?: 'No error body'}"

                throw new RuntimeException(errorMessage)
            }

        } catch (IOException e) {
            // Handle network/connection level errors
            throw new RuntimeException("IOException during request to ${requestUrl}: ${e.getMessage()}", e)
        } catch (Exception e) {
            // Catch other potential errors (like URL format, etc.) and rethrow
             if (e instanceof RuntimeException) throw e // Avoid wrapping RuntimeExceptions again
             throw new RuntimeException("Unexpected error during request to ${requestUrl}: ${e.getMessage()}", e)
        } finally {
            // --- Ensure Disconnect ---
            if (connection != null) {
                connection.disconnect()
            }
        }
    }

    private Map parseJson(String jsonString, String callingMethod) {
        try {
            def output = new JsonSlurper().parseText(jsonString)

            if (output !instanceof Map) {
                throw new RuntimeException("Expected JSON response to be a Map, but got: ${output}")
            }

            return output as Map
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON response in ${callingMethod}: ${e.getMessage()}", e)
        }
    }

    private void updateAccessToken() {
        try {
            this.accessToken = fetchAccessToken()
            log.debug("Access token refreshed successfully.")
        } catch (Exception e) {
            log.error("Failed to refresh access token: ${e.message}", e)
            throw new RuntimeException("Failed to refresh access token.", e)
        }
    }
}
