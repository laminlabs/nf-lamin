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
 * The configuration is extracted from the nextflow.config file under the lamin tag. For example:
 *
 * lamin {
 *   instance = "laminlabs/lamindata"
 *   access_token = System.getenv("LAMIN_API_KEY")
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
    final protected String accessToken

    /**
     * Constructor for the LaminConfig class
     * @param session the Nextflow session
     */
    LaminConfig(final Session session) {
        final Map config = session.config?.navigate('lamin') as Map ?: [:]

        // check if all values are available
        // NOTE: disable this check for now
        // assert config.instance, "config 'lamin.instance' is required"
        // assert config.access_token, "config 'lamin.access_token' is required"
        if (config.instance == null) {
            throw new IllegalArgumentException("Lamin instance is not set. Please set the 'lamin.instance' in your nextflow.config file.")
        }
        if (config.instance !instanceof String) {
            throw new IllegalArgumentException("Lamin instance is not a string. Please set the 'lamin.instance' in your nextflow.config file.")
        }
        if (config.access_token == null) {
            throw new IllegalArgumentException("Lamin access token is not set. Please set the 'lamin.access_token' in your nextflow.config file.")
        }
        if (config.access_token !instanceof String) {
            throw new IllegalArgumentException("Lamin access token is not a string. Please set the 'lamin.access_token' in your nextflow.config file.")
        }

        // store values
        this.instance = config.instance as String
        this.accessToken = config.access_token as String
    }

    /**
     * Get the instance for the Lamin API
     * @return the instance
     */
    String getInstance() {
        return this.instance
    }

    /**
     * Get the access token for the Lamin API
     * @return the access token
     */
    String getAccessToken() {
        return this.accessToken
    }

}
