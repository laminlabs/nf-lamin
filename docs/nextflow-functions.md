# Functions & Lamin URIs

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
