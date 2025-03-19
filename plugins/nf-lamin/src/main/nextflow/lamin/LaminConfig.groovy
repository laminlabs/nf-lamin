package nextflow.lamin

import groovy.transform.PackageScope


/**
 * Handle the configuration of the Lamin plugin
 *
 * The configuration is extracted from the nextflow.config file under the lamin tag, e.g.
 *
 * lamin {
 *   instance = "laminlabs/lamindata"
 *   access_token = System.getenv("LAMIN_API_KEY")
 * }
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 *
 */
@PackageScope
class LaminConfig {
    // NOTE: There is a much more elegant interface since Nextflow >= 25.02.0:
    // https://www.nextflow.io/docs/latest/developer/plugins.html#configuration
    // However, since this would require a newer version of Nextflow, we will stick to the current implementation.

    final private String instance
    final private String access_token

    LaminConfig(Map map){
        def config = map ?: Collections.emptyMap()

        // check if all values are available
        // NOTE: disable this check for now 
        // assert config.instance, "config 'lamin.instance' is required"
        // assert config.access_token, "config 'lamin.access_token' is required"

        // store values
        instance = config.instance
        access_token = config.access_token
    }

    String getInstance() { instance }
    String getAccessToken() { access_token }
}
