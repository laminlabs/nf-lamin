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

package ai.lamin.nf_lamin.cli

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.cli.PluginAbstractExec

import ai.lamin.nf_lamin.LaminConfig
import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.hub.LaminHubSettings
import ai.lamin.nf_lamin.hub.InstanceSettings
import ai.lamin.nf_lamin.instance.Instance

/**
 * Implements the {@code nextflow plugin nf-lamin:<command>} CLI entry point.
 *
 * <p>Low-level API commands for troubleshooting the LaminDB REST API. All commands
 * connect via the {@code lamin { }} config block (or the {@code LAMIN_API_KEY} /
 * {@code LAMIN_CURRENT_INSTANCE} env vars) and print JSON to stdout.
 *
 * <pre>
 * nextflow plugin nf-lamin:call-api get-account
 *
 * nextflow plugin nf-lamin:call-api get-schema
 * nextflow plugin nf-lamin:call-api get-record    --module core --model transform --id <uid>
 * nextflow plugin nf-lamin:call-api get-records   --module core --model transform [--limit 50] [--filter '{"key":"val"}']
 * nextflow plugin nf-lamin:call-api create-record --module core --model run --data '{"field":"value"}'
 * nextflow plugin nf-lamin:call-api update-record --module core --model transform --uid <uid> --data '{"field":"value"}'
 * nextflow plugin nf-lamin:call-api delete-record --module core --model transform --uid <uid>
 * nextflow plugin nf-lamin:call-api upsert-record --module core --model artifactproject --conflict-columns artifact_id,project_id --data '{"artifact_id":1,"project_id":2}'
 * nextflow plugin nf-lamin:call-api batch-update  --module core --model artifact --index-columns id --records '[{"id":1,"description":"x"}]'
 * nextflow plugin nf-lamin:call-api batch-delete  --module core --model artifactproject --records '[{"artifact_id":1,"project_id":2}]'
 *
 * nextflow plugin nf-lamin:call-api create-transform --key <key> --kind pipeline --source-code "# src"
 * nextflow plugin nf-lamin:call-api create-artifact  --path s3://bucket/key [--kwarg value ...]
 * nextflow plugin nf-lamin:call-api upload-artifact  --file /local/path      [--kwarg value ...]
 * </pre>
 *
 * Set {@code CALL_API_ENABLED = false} before releasing to disable this command.
 */
@Slf4j
@CompileStatic
class LaminCmdEntry implements PluginAbstractExec {

    // Set to false before releasing to disable the call-api command
    private static final boolean CALL_API_ENABLED = true

    private static final List<String> CALL_API_SUBCOMMANDS = [
        'get-account', 'get-schema',
        'get-record', 'get-records',
        'create-record', 'update-record', 'delete-record',
        'upsert-record', 'batch-update', 'batch-delete',
        'create-transform',
        'create-artifact', 'upload-artifact',
    ]

    @Override
    List<String> getCommands() {
        return CALL_API_ENABLED ? ['call-api'] : []
    }

    @Override
    int exec(String cmd, List<String> args) {
        if (!CALL_API_ENABLED) {
            System.err.println("Unknown command: ${cmd}")
            return 1
        }
        if (cmd != 'call-api') {
            System.err.println("Unknown command: ${cmd}. Use: nextflow plugin nf-lamin:call-api <subcommand>")
            return 1
        }
        if (!args || args[0].startsWith('--')) {
            System.err.println("Usage: nextflow plugin nf-lamin:call-api <subcommand> [options]")
            System.err.println("Available subcommands: ${CALL_API_SUBCOMMANDS.join(', ')}")
            return 1
        }

        String subCmd = args[0]
        List<String> subArgs = args.drop(1)

        LaminConfig config = LaminConfig.parseConfig(session)
        LaminHubSettings hubSettings = LaminHubSettings.resolve(config)
        LaminHub hub = new LaminHub(hubSettings.supabaseApiUrl, hubSettings.supabaseAnonKey, config.apiKey)
        InstanceSettings instanceSettings = hub.getInstanceSettings(config.instanceOwner, config.instanceName)
        Instance instance = new Instance(hub, instanceSettings, config.apiConfig.maxRetries, config.apiConfig.retryDelay, config.apiConfig.maxRetryDelay)
        Map<String, String> params = parseArgs(subArgs)

        switch (subCmd) {
            case 'get-account':      return cmdGetAccount(instance)
            case 'get-schema':       return cmdGetSchema(instance)
            case 'get-record':       return cmdGetRecord(instance, params)
            case 'get-records':      return cmdGetRecords(instance, params)
            case 'create-record':    return cmdCreateRecord(instance, params)
            case 'update-record':    return cmdUpdateRecord(instance, params)
            case 'delete-record':    return cmdDeleteRecord(instance, params)
            case 'upsert-record':    return cmdUpsertRecord(instance, params)
            case 'batch-update':     return cmdBatchUpdate(instance, params)
            case 'batch-delete':     return cmdBatchDelete(instance, params)
            case 'create-transform': return cmdCreateTransform(instance, params)
            case 'create-artifact':  return cmdCreateArtifact(instance, params)
            case 'upload-artifact':  return cmdUploadArtifact(instance, params)
            default:
                System.err.println("Unknown subcommand: ${subCmd}")
                System.err.println("Available subcommands: ${CALL_API_SUBCOMMANDS.join(', ')}")
                return 1
        }
    }

    // -------------------------------------------------------------------------
    // Command implementations
    // -------------------------------------------------------------------------

    private int cmdGetAccount(Instance instance) {
        return run('get-account', null) { instance.getAccount() }
    }

    private int cmdGetSchema(Instance instance) {
        return run('get-schema', null) { instance.getSchema() }
    }

    // -- transform commands ---------------------------------------------------

    private int cmdCreateTransform(Instance instance, Map<String, String> p) {
        if (!requireParams(p, 'call-api create-transform', '--key <key> --kind pipeline --source-code "# src" [--version-tag v1]', 'key', 'kind', 'source-code')) return 1
        Map<String, Object> args = [
            key: p['key'],
            kind: p['kind'],
            source_code: p['source-code'],
        ]
        if (p['version-tag'])    args['version_tag']    = p['version-tag']
        if (p['reference'])      args['reference']      = p['reference']
        if (p['reference-type']) args['reference_type'] = p['reference-type']
        if (p['description'])    args['description']    = p['description']
        return run('create-transform', null) { instance.createTransform(args) }
    }

    // -- record commands -------------------------------------------------------

    private int cmdGetRecord(Instance instance, Map<String, String> p) {
        if (!requireParams(p, 'call-api get-record', '--module core --model transform --id <uid>', 'module', 'model', 'id')) return 1
        Map<String, Object> args = [
            moduleName: p.module,
            modelName: p.model,
            idOrUid: p.id,
        ] as Map<String, Object>
        if (p.limit_to_many) args.limitToMany = p.limit_to_many as Integer
        if (p.include_fk)    args.includeForeignKeys = true
        if (p.select)        args.select = p.select.split(',').toList()
        return run('get-record', null) { instance.getRecord(args) }
    }

    private int cmdGetRecords(Instance instance, Map<String, String> p) {
        if (!requireParams(p, 'call-api get-records', '--module core --model transform [--limit 50] [--offset 0] [--search text] [--filter \'{"key":"val"}\'] [--select field1,field2]', 'module', 'model')) return 1
        Map<String, Object> args = [
            moduleName: p.module,
            modelName: p.model,
        ] as Map<String, Object>
        if (p.limit)         args.limit = p.limit as Integer
        if (p.offset)        args.offset = p.offset as Integer
        if (p.limit_to_many) args.limitToMany = p.limit_to_many as Integer
        if (p.include_fk)    args.includeForeignKeys = true
        if (p.search)        args.search = p.search
        if (p.filter)        args.filter = new JsonSlurper().parseText(p.filter) as Map<String, Object>
        if (p.select)        args.select = p.select.split(',').toList()
        return run('get-records', null) { instance.getRecords(args) }
    }

    private int cmdCreateRecord(Instance instance, Map<String, String> p) {
        if (!requireParams(p, 'call-api create-record', '--module core --model run --data \'{"field":"value"}\'', 'module', 'model', 'data')) return 1
        Map<String, Object> args = [
            moduleName: p.module,
            modelName: p.model,
            data: new JsonSlurper().parseText(p.data) as Map,
        ] as Map<String, Object>
        return run('create-record', null) { instance.createRecord(args) }
    }

    private int cmdUpdateRecord(Instance instance, Map<String, String> p) {
        if (!requireParams(p, 'call-api update-record', '--module core --model transform --uid <uid> --data \'{"field":"value"}\'', 'module', 'model', 'uid', 'data')) return 1
        Map<String, Object> args = [
            moduleName: p.module,
            modelName: p.model,
            uid: p.uid,
            data: new JsonSlurper().parseText(p.data) as Map,
        ] as Map<String, Object>
        return run('update-record', null) { instance.updateRecord(args) }
    }

    private int cmdDeleteRecord(Instance instance, Map<String, String> p) {
        if (!requireParams(p, 'call-api delete-record', '--module core --model transform --uid <uid>', 'module', 'model', 'uid')) return 1
        Map<String, Object> args = [
            moduleName: p.module,
            modelName: p.model,
            uid: p.uid,
        ] as Map<String, Object>
        return run('delete-record', null) { instance.deleteRecord(args) }
    }

    private int cmdUpsertRecord(Instance instance, Map<String, String> p) {
        if (!requireParams(p, 'call-api upsert-record', '--module core --model artifactproject --conflict-columns artifact_id,project_id --data \'{"artifact_id":1,"project_id":2}\'', 'module', 'model', 'conflict-columns', 'data')) return 1
        Map<String, Object> args = [
            moduleName: p.module,
            modelName: p.model,
            conflictColumns: p['conflict-columns'].split(',').toList(),
            data: new JsonSlurper().parseText(p.data),
        ] as Map<String, Object>
        return run('upsert-record', null) { instance.upsertRecord(args) }
    }

    private int cmdBatchUpdate(Instance instance, Map<String, String> p) {
        if (!requireParams(p, 'call-api batch-update', '--module core --model artifact --index-columns id --records \'[{"id":1,"field":"value"}]\'', 'module', 'model', 'index-columns', 'records')) return 1
        Map<String, Object> args = [
            moduleName: p.module,
            modelName: p.model,
            indexColumns: p['index-columns'].split(',').toList(),
            records: new JsonSlurper().parseText(p.records) as List,
        ] as Map<String, Object>
        return run('batch-update', null) { instance.batchUpdateRecords(args) }
    }

    private int cmdBatchDelete(Instance instance, Map<String, String> p) {
        if (!requireParams(p, 'call-api batch-delete', '--module core --model artifactproject --records \'[{"artifact_id":1,"project_id":2}]\'', 'module', 'model', 'records')) return 1
        Map<String, Object> args = [
            moduleName: p.module,
            modelName: p.model,
            records: new JsonSlurper().parseText(p.records) as List,
        ] as Map<String, Object>
        return run('batch-delete', null) { instance.batchDeleteRecords(args) }
    }

    // -- artifact commands -----------------------------------------------------

    private int cmdCreateArtifact(Instance instance, Map<String, String> p) {
        if (!requireParams(p, 'call-api create-artifact', '--path s3://bucket/key [--kwarg value ...]', 'path')) return 1
        // All flags become kwargs; 'path' is handled separately inside Instance.createArtifact
        Map<String, Object> args = new HashMap<String, Object>(p as Map<String, Object>)
        return run('create-artifact', null) { instance.createArtifact(args) }
    }

    private int cmdUploadArtifact(Instance instance, Map<String, String> p) {
        if (!requireParams(p, 'call-api upload-artifact', '--file /local/path [--kwarg value ...]', 'file')) return 1
        Map<String, Object> args = new HashMap<String, Object>(p as Map<String, Object>)
        args.file = new File(p.file)
        return run('upload-artifact', null) { instance.uploadArtifact(args) }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Runs a closure, prints JSON result, returns 0 on success or 1 on failure. */
    private int run(String label, @SuppressWarnings('unused') Void unused, Closure<?> action) {
        try {
            Object result = action.call()
            // serialize via the client Gson so typed models (e.g. Account) print with
            // their API field names
            println JsonOutput.prettyPrint(ai.lamin.lamin_api_client.JSON.getGson().toJson(result))
            return 0
        } catch (Exception e) {
            System.err.println("${label} failed: ${e.message}")
            return 1
        }
    }

    /** Validates that all required parameter keys are present; prints usage if not. */
    private static boolean requireParams(Map<String, String> p, String cmd, String usage, String... required) {
        List<String> missing = required.findAll { !p.containsKey(it) }
        if (missing) {
            System.err.println("Error: missing required parameter(s): ${missing.collect { '--' + it }.join(', ')}")
            System.err.println("Usage: nextflow plugin nf-lamin:${cmd} ${usage}")
            return false
        }
        return true
    }

    /**
     * Parses {@code ["--key", "value", ...]} tokens into a map.
     * Boolean flags (no following value) are stored as {@code "true"}.
     */
    private static Map<String, String> parseArgs(List<String> args) {
        Map<String, String> params = [:]
        for (int i = 0; i < args.size(); i++) {
            String arg = args[i]
            if (arg.startsWith('--')) {
                String key = arg.substring(2)
                if (i + 1 < args.size() && !args[i + 1].startsWith('--')) {
                    params[key] = args[++i]
                } else {
                    params[key] = 'true'
                }
            }
        }
        return params
    }
}
