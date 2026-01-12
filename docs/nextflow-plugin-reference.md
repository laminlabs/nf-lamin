# `nf-lamin` plugin reference

## Configuration

### Basic Configuration

Add the following block to your `nextflow.config`:

```groovy
plugins {
  id 'nf-lamin'
}

lamin {
  instance = "<your-lamin-org>/<your-lamin-instance>"
  api_key = secrets.LAMIN_API_KEY
  project = "your-lamin-project" // optional
}
```

**Settings:**

- `lamin.instance`: **(Required)** The LaminDB instance to connect to, in the format `organization/instance`.
- `lamin.api_key`: **(Required)** Your Lamin Hub API key. It is strongly recommended to set this using `nextflow secrets`.
- `lamin.project`: (Optional) The project name in LaminDB to associate runs with.

Alternatively, you can use environment variables, though this is less secure:

```bash
export LAMIN_CURRENT_INSTANCE="laminlabs/lamindata"
export LAMIN_API_KEY="your-lamin-api-key"
export LAMIN_CURRENT_PROJECT="your-lamin-project"
```

### Advanced Configuration

The plugin offers advanced settings for custom deployments or for tuning its behavior.

```groovy
lamin {
  // ... basic settings ...

  // The environment name in LaminDB (e.g. "prod" or "staging")
  env = "prod"
  // Enable dry-run mode to test configuration without creating records
  dry_run = false
  // The Supabase API URL for the LaminDB instance (if env is set to "custom")
  supabase_api_url = "https://your-supabase-api-url.supabase.co"
  // The Supabase anon key for the LaminDB instance (if env is set to "custom")
  supabase_anon_key = secrets.SUPABASE_ANON_KEY
  // The number of retries for API requests
  max_retries = 3
  // The delay between retries in milliseconds
  retry_delay = 100
  // Manually specify a transform UID if known (advanced users only)
  transform_uid = "your-transform-uid"
  // Manually specify a run UID if known (advanced users only)
  run_uid = "your-run-uid"
}
```

You can also set these using environment variables:

```bash
export LAMIN_ENV="prod"
export LAMIN_DRY_RUN="false"
export SUPABASE_API_URL="https://your-supabase-api-url.supabase.co"
export SUPABASE_ANON_KEY="your-supabase-anon-key"
export LAMIN_MAX_RETRIES=3
export LAMIN_RETRY_DELAY=100
export LAMIN_TRANSFORM_UID="your-transform-uid"
export LAMIN_RUN_UID="your-run-uid"
```

**Advanced settings explained:**

- `env`: Environment selector for LaminDB instance (e.g., "prod", "staging", or "custom")
- `dry_run`: When `true`, the plugin validates configuration and connects to LaminDB but does not create or modify any records (transforms, runs, or artifacts). Useful for testing your setup without affecting the database.
- `supabase_api_url` & `supabase_anon_key`: Custom Supabase connection details (only needed if `env = "custom"`)
- `max_retries` & `retry_delay`: Control retry behavior for API requests
- `transform_uid` & `run_uid`: Manually override transform/run UIDs (advanced usage only)

## Functions

### `getRunUid()`

Returns the UID of the current Lamin run.

**Returns:** `String` - The run UID, or `null` if the plugin hasn't initialized the run yet.

**Example:**

```groovy
include { getRunUid } from 'plugin/nf-lamin'

workflow {
  def runUid = getRunUid()
  log.info "Current run: ${runUid}"
}
```

### `getTransformUid()`

Returns the UID of the current Lamin transform.

**Returns:** `String` - The transform UID, or `null` if the plugin hasn't initialized the transform yet.

**Example:**

```groovy
include { getTransformUid } from 'plugin/nf-lamin'

workflow {
  def transformUid = getTransformUid()
  log.info "Current transform: ${transformUid}"
}
```

### `getInstanceSlug()`

Returns the currently configured LaminDB instance identifier.

**Returns:** `String` - The instance slug in the format "owner/name" (e.g., "laminlabs/lamindata"), or `null` if not available.

**Example:**

```groovy
include { getInstanceSlug } from 'plugin/nf-lamin'

workflow {
  def instance = getInstanceSlug()
  log.info "Connected to LaminDB instance: ${instance}"
}
```

## Lamin URIs

The plugin provides native support for `lamin://` URIs, allowing you to reference LaminDB artifacts directly in your Nextflow workflows using Nextflow's standard `file()` function.

```
lamin://<owner>/<instance>/artifact/<uid>[/<subpath>]
```

**Components:**

- `owner` - The LaminDB instance owner (organization or user)
- `instance` - The LaminDB instance name
- `uid` - The artifact UID (16 or 20 characters)
  - 16-character base UIDs fetch the most recently updated version
  - 20-character full UIDs fetch that specific version
- `subpath` - (Optional) Path within the artifact for directories or archives

### Basic Usage

Use `lamin://` URIs with the `file()` function:

```groovy
workflow {
  // Reference a LaminDB artifact directly by URI
  def input_file = file('lamin://laminlabs/lamindata/artifact/PnNjE93TdZGJ')

  log.info "Using artifact: ${input_file}"

  Channel.of(input_file)
    | myProcess
}
```

### With Sub-paths

For artifacts that are directories or archives, reference specific files within them:

```groovy
workflow {
  // Reference a specific file within an artifact directory
  def config_file = file('lamin://myorg/myinstance/artifact/abcd1234efgh5678/config/settings.yaml')

  Channel.of(config_file)
    | processConfig
}
```

### As Workflow Parameters

Use `lamin://` URIs as workflow parameters:

```groovy
params.input = 'lamin://laminlabs/lamindata/artifact/PnNjE93TdZGJ'

workflow {
  Channel.fromPath(params.input)
    | myProcess
}
```

Or pass them on the command line:

```bash
nextflow run my-pipeline.nf --input 'lamin://laminlabs/lamindata/artifact/PnNjE93TdZGJ'
```

### Requirements

- The `nf-lamin` plugin must be configured with a valid API key
- The workflow must have started (the plugin initializes on workflow start)
- For private cloud storage, ensure your AWS/GCS credentials are configured in `nextflow.config`

### Limitations

- `lamin://` paths are **read-only** - you cannot write to them
- Currently uses cloud credentials from your `nextflow.config` (automatic credential federation from Lamin Hub is planned for a future release)
