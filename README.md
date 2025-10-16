# nf-lamin

[Nextflow](https://www.nextflow.io/) is a widely used workflow manager designed for scalable and reproducible data analysis in bioinformatics.

**The `nf-lamin` plugin provides data provenance tracking for your Nextflow workflows via [Lamin Hub](https://lamin.ai/).** It automatically captures workflow executions, parameters, code versions, and input/output files as structured metadata in LaminDB, enabling full lineage tracking and reproducibility of your computational analyses. For detailed instructions, please see the [plugin documentation](https://docs.lamin.ai/nextflow).

LaminDB integrates with Nextflow through:

1. The `nf-lamin` Nextflow plugin (recommended)
2. A post-run Python script (for custom solutions)

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

### Advanced usage: Accessing run metadata

For advanced use cases where you need to access Lamin run information from within your Nextflow workflow, the plugin provides two helper functions:

```groovy
include { getRunUid; getTransformUid } from 'plugin/nf-lamin'

workflow {
  // Get the current Lamin run UID
  def runUid = getRunUid()
  log.info "Current Lamin run UID: ${runUid}"

  // Get the current Lamin transform UID
  def transformUid = getTransformUid()
  log.info "Current Lamin transform UID: ${transformUid}"

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
