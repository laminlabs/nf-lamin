# nf-lamin

[Nextflow](https://www.nextflow.io/) is a widely used workflow manager designed for scalable and reproducible data analysis in bioinformatics.

**The `nf-lamin` plugin provides data provenance tracking for your Nextflow workflows via [Lamin Hub](https://lamin.ai/).** It automatically captures workflow executions, parameters, code versions, and input/output files as structured metadata in LaminDB, enabling full lineage tracking and reproducibility of your computational analyses. For detailed instructions, please see the [plugin documentation](https://docs.lamin.ai/nextflow).

LaminDB integrates with Nextflow through:

1. The `nf-lamin` Nextflow plugin (recommended)
2. A post-run Python script (for custom solutions)

## Version Compatibility

| nf-lamin  | LaminDB       | Nextflow   | Status         | Key Features                                                          |
| --------- | ------------- | ---------- | -------------- | --------------------------------------------------------------------- |
| **0.4.x** | >= 2.0        | >= 25.10.0 | ✅ Supported   | Input artifact tracking                                               |
| **0.3.0** | >= 2.0        | >= 25.04.0 | ✅ Supported   | Upgrade to LaminDB v2, Add support for `lamin://` URI support         |
| **0.2.2** | >= 1.0, < 2.0 | >= 25.04.0 | ✅ Supported   | Added `getInstanceSlug()`                                             |
| 0.2.1     | >= 1.0, < 2.0 | >= 25.04.0 | ❌ Unsupported | Added report upload, `getRunUid()`, `getTransformUid()`, dry-run mode |
| 0.2.0     | >= 1.0, < 2.0 | >= 25.04.0 | ❌ Unsupported | Specify transform/run UID                                             |
| 0.1.0     | >= 1.0, < 2.0 | >= 24.04.0 | ❌ Unsupported | Added Transform & Run tracking, output artifact registration          |
| 0.0.1     | >= 1.0, < 2.0 | >= 24.04.0 | ❌ Unsupported | Initial release                                                       |

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

  // Optional: Configure artifact tracking (see below for details)
  output_artifacts {
    enabled = true
    include_pattern = '.*\\.(fastq|bam)$'
    exclude_pattern = '.*temp.*'
  }
}
```

#### Artifact Tracking Configuration

Control which files are tracked and attach metadata to artifacts using pattern-based rules. You can configure tracking either globally (for all artifacts) or separately for inputs and outputs, but **not both** (they are mutually exclusive).

**Option 1: Global configuration** (applies to both inputs and outputs)

```groovy
lamin {
  artifacts {
    enabled = true
    ulabel_uids = ['global-label-uid']
    project_uids = ['global-project-uid']
    exclude_pattern = '.*\\.tmp$'

    rules {
      fastqs {
        pattern = '.*\\.fastq\\.gz$'
        ulabel_uids = ['fastq-label-uid']
        kind = 'dataset'
      }
    }
  }
}
```

**Option 2: Direction-specific configuration** (separate configs for inputs and outputs)

```groovy
lamin {
  input_artifacts {
    enabled = true
    include_pattern = '.*\\.(fastq|fq)\\.gz$'

    rules {
      reference_genomes {
        pattern = '.*reference.*\\.fasta$'
        ulabel_uids = ['reference-data-uid']
        kind = 'reference'
      }
    }
  }

  output_artifacts {
    enabled = true
    exclude_pattern = '.*\\.tmp$'

    rules {
      bams {
        enabled = false  // Disable tracking for BAM files
        pattern = '.*\\.bam$'
      }
    }
  }
}
```

**Configuration options:**

- `enabled` - Enable/disable tracking (default: true)
- `include_pattern` - Regex pattern; files must match to be tracked
- `exclude_pattern` - Regex pattern; matching files won't be tracked
- `ulabel_uids` - List of ULabel UIDs to attach to artifacts
- `project_uids` - List of Project UIDs to attach to artifacts
- `kind` - Artifact kind (e.g., 'dataset', 'model', 'reference')
- `rules` - Path-specific configurations:
  - `pattern` - Regex pattern to match file paths (required)
  - `enabled` - Enable/disable this rule (default: true)
  - `type` - 'include' or 'exclude' (default: 'include')
  - `direction` - 'input', 'output', or 'both' (default: inherited from parent config)
  - `order` - Evaluation priority (lower numbers first, default: 100)
  - `ulabel_uids` - List of ULabel UIDs to attach to matching artifacts
  - `project_uids` - List of Project UIDs to attach to matching artifacts
  - `kind` - Artifact kind for matching files

**Evaluation order:**

1. Global patterns (`include_pattern`, `exclude_pattern`) are checked first
2. Rules are evaluated in order of priority (`order` field, lower numbers first)
3. All matching rules are processed - each can modify the tracking decision
4. Later `include` rules can override earlier `exclude` rules, and vice versa
5. Metadata (ulabels, projects) from all matching rules is accumulated; `kind` uses the last match

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
