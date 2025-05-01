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
     * Configuration for Lamin API integration
     * @param instance format: 'owner/repo'.
     * @param apiKey LaminDB API authorization key
     * @param project the project for the Lamin API
     * @throws IllegalArgumentException if any of the parameters are null or invalid
     */
    LaminConfig(String instance, String apiKey, String project = null) {
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

        // store values
        this.instance = instance
        this.apiKey = apiKey
        this.project = project
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
     * Create a LaminConfig object from the Nextflow session and environment variables
     * @param session the Nextflow session
     * @return a LaminConfig object
     */
    @PackageScope
    static LaminConfig createFromSession(Session session) {
        if (session == null) {
            throw new IllegalArgumentException('Session is null. Please provide a valid session.')
        }
        Map map = session.config?.navigate('lamin') as Map ?: [:]
        String instance = map.instance ?: System.getenv('LAMIN_CURRENT_INSTANCE')
        String apiKey = map.api_key ?: System.getenv('LAMIN_API_KEY')
        String project = map.project ?: System.getenv('LAMIN_CURRENT_PROJECT')
        return new LaminConfig(instance, apiKey, project)
    }

}
