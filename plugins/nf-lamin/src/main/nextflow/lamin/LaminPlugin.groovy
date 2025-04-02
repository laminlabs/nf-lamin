/*
 * Copyright 2025, Lamin Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.lamin

import groovy.transform.CompileStatic
import nextflow.plugin.BasePlugin
import nextflow.plugin.Scoped
import org.pf4j.PluginWrapper

// temporary:
import nextflow.cli.PluginAbstractExec
import com.data_intuitive.lamin_api_client_java.ApiClient;
import com.data_intuitive.lamin_api_client_java.ApiException;
import com.data_intuitive.lamin_api_client_java.Configuration;
import com.data_intuitive.lamin_api_client_java.models.*;
import com.data_intuitive.lamin_api_client_java.api.DefaultApi;

/**
 * Implements the Lamin plugins entry point
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
@CompileStatic
class LaminPlugin extends BasePlugin implements PluginAbstractExec {

    // /*
    //  * A Custom config extracted from nextflow.config under lamin tag
    //  * nextflow.config
    //  * ---------------
    //  * lamin {
    //  *   instance = "laminlabs/lamindata"
    //  *   access_token = System.getenv("LAMIN_API_KEY")
    //  * }
    //  */
    // private LaminConfig config

    LaminPlugin(PluginWrapper wrapper) {
        super(wrapper)
        // this.config = new LaminConfig(session.config.navigate('lamin') as Map)
    }

    // temporary
    @Override
    List<String> getCommands() {
        [ 'test-lamin-connection' ]
    }

    @Override
    int exec(String cmd, List<String> args) {
        if( cmd == 'test-lamin-connection' ) {
            println "Hello! You gave me these arguments: ${args.join(' ')}"

            ApiClient defaultClient = Configuration.getDefaultApiClient();
            defaultClient.setBasePath("https://aws.us-east-1.lamin.ai/api");

            DefaultApi apiInstance = new DefaultApi(defaultClient);
            
            UUID instanceId = UUID.fromString("037ba1e08d804f91a90275a47735076a");
            String moduleName = "core";
            String modelName = "artifact";
            String idOrUid = "MDG7BbeFVPvEyyUb0000";
            Integer limitToMany = 10;
            Boolean includeForeignKeys = true;
            UUID schemaId = UUID.fromString("185ae0d36f3fce2a8122516d30520816");
            String authorization = "...";
            GetRecordRequestBody getRecordRequestBody = new GetRecordRequestBody();
            try {
                Object result = apiInstance.getRecordInstancesInstanceIdModulesModuleNameModelNameIdOrUidPost(moduleName, modelName, idOrUid, instanceId, limitToMany, includeForeignKeys, schemaId, authorization, getRecordRequestBody);
                System.out.println(result);
            } catch (ApiException e) {
                System.err.println("Exception when calling DefaultApi#getRecordInstancesInstanceIdModulesModuleNameModelNameIdOrUidPost");
                System.err.println("Status code: " + e.getCode());
                System.err.println("Reason: " + e.getResponseBody());
                System.err.println("Response headers: " + e.getResponseHeaders());
                e.printStackTrace();
            }
            return 0
        }
        else {
            System.err.println "Invalid command: ${cmd}"
            return 1
        }
    }
}
