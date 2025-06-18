package nextflow.lamin

import groovy.transform.PackageScope
import groovy.transform.CompileStatic
import nextflow.Session

// NOTE: There is a much more elegant interface since Nextflow >= 25.02.0:
// https://www.nextflow.io/docs/latest/developer/plugins.html#configuration
// However, since this would require a newer version of Nextflow, we will stick to the current implementation.

/**
 * Handle the configuration of the Lamin plugin
 *
 * These settings can be defined in the nextflow config as follows:
 *
 * lamin {
 *   instance = 'laminlabs/lamindata'
 *   api_key = System.getenv('LAMIN_API_KEY')
 *   project = System.getenv('LAMIN_CURRENT_PROJECT')
 * }
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
@PackageScope
@CompileStatic
class LaminConfig {

    private final Map hubLookup = [
        prod: [
            webUrl: 'https://lamin.ai',
            apiUrl: 'https://hub.lamin.ai',
            anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxhZXNhdW1tZHlkbGxwcGdmY2h1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2NTY4NDA1NTEsImV4cCI6MTk3MjQxNjU1MX0.WUeCRiun0ExUxKIv5-CtjF6878H8u26t0JmCWx3_2-c'
        ],
        staging: [
            webUrl: "https://staging.laminhub.com",
            apiUrl: 'https://amvrvdwndlqdzgedrqdv.supabase.co',
            anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFtdnJ2ZHduZGxxZHpnZWRycWR2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2NzcxNTcxMzMsImV4cCI6MTk5MjczMzEzM30.Gelt3dQEi8tT4j-JA36RbaZuUvxRnczvRr3iyRtzjY0'
        ],
        'staging-test': [
            webUrl: "https://staging-test.laminhub.com",
            apiUrl: 'https://iugyyajllqftbpidapak.supabase.co',
            anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml1Z3l5YWpsbHFmdGJwaWRhcGFrIiwicm9sZSI6ImFub24iLCJpYXQiOjE2OTQyMjYyODMsImV4cCI6MjAwOTgwMjI4M30.s7B0gMogFhUatMSwlfuPJ95kWhdCZMn1ROhZ3t6Og90'
        ],
        'prod-test': [
            webUrl: "https://prod-test.laminhub.com",
            apiUrl: 'https://xtdacpwiqwpbxsatoyrv.supabase.co',
            anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh0ZGFjcHdpcXdwYnhzYXRveXJ2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2OTQyMjYxNDIsImV4cCI6MjAwOTgwMjE0Mn0.Dbi27qujTt8Ei9gfp9KnEWTYptE5KUbZzEK6boL46k4'
        ]
    ]

    /**
     * The instance for the Lamin API
     */
    final protected String instance

    /**
     * The access token for the Lamin API
     */
    final protected String apiKey

    /**
     * The project for the Lamin API
     */
    final protected String project

    /**
     * The environment for the Lamin API
     */
    final protected String env

    /**
     * The Supabase API URL for the Lamin API
     */
    final protected String supabaseApiUrl

    /**
     * The Supabase Anon Key for the Lamin API
     */
    final protected String supabaseAnonKey

    /**
     * Maximum number of retries for API requests
     */
    final protected Integer maxRetries

    /**
     * Delay between retries for API requests in milliseconds
     */
    final protected Integer retryDelay

    /**
     * Configuration for Lamin API integration
     * @param instance format: 'owner/repo'.
     * @param apiKey LaminDB API authorization key
     * @param project the project for the Lamin API
     * @throws IllegalArgumentException if any of the parameters are null or invalid
     */
    LaminConfig(String instance, String apiKey, String project = null, String env = null,
                String supabaseApiUrl = null, String supabaseAnonKey = null, Integer maxRetries = null, Integer retryDelay = null) {
        // check if all values are available
        if (!instance?.trim()) {
            throw new IllegalArgumentException('Lamin instance is not set. Please set the "lamin.instance" in your nextflow.config file.')
        }
        if (!apiKey?.trim()) {
            throw new IllegalArgumentException('Lamin API key is not set. Please set the "lamin.api_key" in your nextflow.config file.')
        }

        // check if instance is <owner>/<repo>
        if (!instance.matches(/^[\w.-]+\/[\w.-]+$/)) {
            throw new IllegalArgumentException('Provided Lamin instance ${instance} is not valid. Please provide a valid instance in the format <owner>/<repo>.')
        }

        if (env) {
            if (!hubLookup.containsKey(env)) {
                throw new IllegalArgumentException("Provided environment '${env}' is not valid. Please provide a valid environment: ${hubLookup.keySet().join(', ')}.")
            }
            supabaseApiUrl = supabaseApiUrl ?: hubLookup[env]['apiUrl']
            supabaseAnonKey = supabaseAnonKey ?: hubLookup[env]['anonKey']
        }

        // store values
        this.instance = instance
        this.apiKey = apiKey
        this.project = project
        this.env = env
        this.supabaseApiUrl = supabaseApiUrl
        this.supabaseAnonKey = supabaseAnonKey
        this.maxRetries = maxRetries ?: 3
        this.retryDelay = retryDelay ?: 100
    }

    /**
     * Get the instance for the Lamin API
     * @return the instance
     */
    String getInstance() {
        return this.instance
    }

    /**
     * Get the instance owner for the Lamin API
     * @return the instance owner
     */
    String getInstanceOwner() {
        return this.instance.split('/')[0]
    }

    /**
     * Get the instance name for the Lamin API
     * @return the instance name
     */
    String getInstanceName() {
        return this.instance.split('/')[1]
    }

    /**
     * Get the API key for Lamin Hub
     * @return the API key
     */
    String getApiKey() {
        return this.apiKey
    }

    /**
     * Get the project for the Lamin API
     * @return the project
     */
    String getProject() {
        return this.project
    }

    /**
     * Get the environment for the Lamin API
     * @return the environment
     */
    String getEnv() {
        return this.env
    }

    String getWebUrl() {
        return this.hubLookup[this.env ?: 'prod']['webUrl']
    }

    /**
     * Get the Supabase API URL for the Lamin API
     * @return the Supabase API URL
     */
    String getSupabaseApiUrl() {
        return this.supabaseApiUrl
    }

    /**
     * Get the Supabase Anon Key for the Lamin API
     * @return the Supabase Anon Key
     */
    String getSupabaseAnonKey() {
        return this.supabaseAnonKey
    }

    /**
     * Get the maximum number of retries for API requests
     * @return the maximum number of retries
     */
    Integer getMaxRetries() {
        return this.maxRetries
    }
    /**
     * Get the delay between retries for API requests
     * @return the delay between retries in milliseconds
     */
    Integer getRetryDelay() {
        return this.retryDelay
    }

    /**
     * Create a LaminConfig object from the Nextflow session and environment variables
     * @param session the Nextflow session
     * @return a LaminConfig object
     */
    @PackageScope
    static LaminConfig parseConfig(Session session) {
        Map map = session.config?.navigate('lamin') as Map ?: [:]
        String instance = map.instance ?: System.getenv('LAMIN_CURRENT_INSTANCE')
        String apiKey = map.api_key ?: System.getenv('LAMIN_API_KEY')
        String project = map.project ?: System.getenv('LAMIN_CURRENT_PROJECT')
        String env = map.env ?: System.getenv('LAMIN_ENV') ?: 'prod'
        String supabaseApiUrl = map.supabase_api_url ?: System.getenv('SUPABASE_API_URL')
        String supabaseAnonKey = map.supabase_anon_key ?: System.getenv('SUPABASE_ANON_KEY')
        Integer maxRetries = (map.max_retries ?: System.getenv('LAMIN_MAX_RETRIES') ?: 3) as Integer
        Integer retryDelay = (map.retry_delay ?: System.getenv('LAMIN_RETRY_DELAY') ?: 100) as Integer
        return new LaminConfig(instance, apiKey, project, env, supabaseApiUrl, supabaseAnonKey, maxRetries, retryDelay)
    }
}
