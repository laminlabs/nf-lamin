# nf-lamin

[Nextflow](https://www.nextflow.io/) is a widely used workflow manager designed for scalable and reproducible data analysis in bioinformatics.

**The `nf-lamin` plugin provides data provenance tracking for your Nextflow workflows via [Lamin Hub](https://lamin.ai/).** It automatically captures workflow executions, parameters, code versions, and input/output files as structured metadata in LaminDB, enabling full lineage tracking and reproducibility of your computational analyses. For detailed instructions, please see the [plugin documentation](https://docs.lamin.ai/nextflow).

LaminDB integrates with Nextflow through:

1. The `nf-lamin` Nextflow plugin (recommended)
2. A post-run Python script (for custom solutions)

## Version Compatibility

| Version   | Min Nextflow | Status         | Key Features                                                    |
| --------- | ------------ | -------------- | --------------------------------------------------------------- |
| **0.3.x** | 25.10.0      | 🚧 Development | Input artifact tracking                                         |
| **0.2.3** | 25.04.0      | 🚧 Development | `lamin://` URI support                                          |
| **0.2.2** | 25.04.0      | ✅ Supported   | `getInstanceSlug()`                                             |
| 0.2.1     | 25.04.0      | ✅ Supported   | Report upload, `getRunUid()`, `getTransformUid()`, dry-run mode |
| 0.2.0     | 25.04.0      | ❌ Unsupported | Specify transform/run UID                                       |
| 0.1.0     | 24.04.0      | ❌ Unsupported | Transform & Run tracking, output artifact registration          |
| 0.0.1     | 24.04.0      | ❌ Unsupported | Initial release                                                 |

## Plugin

### Basic usage

First, store your [Lamin API key](https://lamin.ai/settings) as a secret in your Nextflow configuration:

```bash
nextflow secrets set LAMIN_API_KEY "your_lamin_api_key_here"
```

Then configure the plugin in your `nextflow.config`:

```groovy
plugins {
  id 'nf-lamin'
}

lamin {
  instance = "your-organization/your-instance"
  api_key = secrets.LAMIN_API_KEY
}
```

That's it! Run your pipeline and the plugin will automatically track provenance:

```bash
nextflow run <your-pipeline>
```

```text
N E X T F L O W  ~  version 24.10.5
Launching `...`
✅ Connected to LaminDB instance 'your-organization/your-instance' as 'your-username'
Transform XXXYYYZZZABC0001 (https://lamin.ai/your-organization/your-instance/transform/XXXYYYZZZABC0001)
Run abcdefghijklmnopqrst (https://lamin.ai/your-organization/your-instance/transform/XXXYYYZZZABC0001/abcdefghijklmnopqrst)
```

### Configuration options

The plugin can be configured via the `lamin` scope in `nextflow.config`:

```groovy
lamin {
  // Required: Your LaminDB instance (format: 'owner/instance-name')
  instance = "your-organization/your-instance"

  // Required: API key for authentication
  api_key = secrets.LAMIN_API_KEY

  // Optional: Project namespace (defaults to environment variable LAMIN_CURRENT_PROJECT)
  project = "my-project"

  // Optional: Environment selector - 'prod' or 'staging' (default: 'prod')
  env = "prod"

  // Optional: Dry-run mode - test configuration without creating records (default: false)
  dry_run = false
}
```

### Using artifacts from LaminDB

The plugin provides native support for `lamin://` URIs, allowing you to reference LaminDB artifacts directly in your Nextflow workflows using [standard Nextflow file handling](https://www.nextflow.io/docs/latest/working-with-files.html).

```groovy
workflow {
  // Reference a LaminDB artifact directly by URI
  def input_file = file('lamin://laminlabs/lamindata/artifact/PnNjE93TdZGJ')

  Channel.of(input_file)
    | myProcess
}
```

**URI format:** `lamin://<owner>/<instance>/artifact/<uid>`

- `owner`: The LaminDB instance owner (organization or user)
- `instance`: The LaminDB instance name
- `uid`: The artifact UID (16 or 20 characters)

For private artifacts, ensure your AWS/GCS credentials are configured in `nextflow.config`. See the [plugin reference](https://docs.lamin.ai/nextflow-plugin-reference) for advanced options like sub-paths within artifacts.

### Advanced usage: Accessing run metadata

For advanced use cases where you need to access Lamin run information from within your Nextflow workflow, the plugin provides helper functions:

```groovy
include { getRunUid; getTransformUid; getInstanceSlug } from 'plugin/nf-lamin'

workflow {
  // Get the current Lamin run UID
  def runUid = getRunUid()
  log.info "Current Lamin run UID: ${runUid}"

  // Get the current Lamin transform UID
  def transformUid = getTransformUid()
  log.info "Current Lamin transform UID: ${transformUid}"

  // Get the configured LaminDB instance (e.g., "laminlabs/lamindata")
  def instance = getInstanceSlug()
  log.info "Connected to LaminDB instance: ${instance}"

  // Use these UIDs in your workflow logic
  Channel
    .of("data")
    .map { data ->
      // Example: embed run UID in output filenames or metadata
      [data: data, lamin_run: runUid]
    }
    .view()
}
```

These functions return `null` if the plugin hasn't initialized the run yet, so they're best used in workflow body (not in process definitions).

## Post-run script

We generally recommend using the `nf-lamin` plugin.
However, if lower level LaminDB usage is required, it might be worthwhile writing a custom Python script.

### Usage

For an example, please see the [post-run documentation](https://docs.lamin.ai/nextflow-postrun).

Such a script could be deployed via:

1. A serverless environment trigger (e.g., AWS Lambda)
2. A [post-run script](https://docs.seqera.io/platform-cloud/launch/advanced#pre-and-post-run-scripts) on the Seqera Platform

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about contributing to this repository.
