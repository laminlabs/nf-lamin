package nextflow.lamin

import groovy.transform.PackageScope


/**
 * Handle the configuration of the Lamin plugin
 *
 * The configuration is extracted from the nextflow.config file under the lamin tag, e.g.
 *
 * lamin {
 *   instance_id = "laminlabs/lamindata"
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

    final private String instance_id
    final private String access_token

    LaminConfig(Map map){
        def config = map ?: Collections.emptyMap()

        // check if all values are available
        // NOTE: disable this check for now 
        // assert config.instance_id, "config 'lamin.instance_id' is required"
        // assert config.access_token, "config 'lamin.access_token' is required"

        // store values
        instance_id = config.instance_id
        access_token = config.access_token
    }

    String getInstanceId() { instance_id }
    String getAccessToken() { access_token }
}
