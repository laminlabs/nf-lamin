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
  project_uids = ['proj123456789012'] // optional
  ulabel_uids = ['ulab123456789012']  // optional
}
```

**Settings:**

- `lamin.instance`: **(Required)** The LaminDB instance to connect to, in the format `organization/instance`.
- `lamin.api_key`: **(Required)** Your Lamin Hub API key. It is strongly recommended to set this using `nextflow secrets`.
- `lamin.project_uids`: (Optional) List of project UIDs to link to all artifacts, runs, and transforms.
- `lamin.ulabel_uids`: (Optional) List of ulabel UIDs to link to all artifacts, runs, and transforms.

Alternatively, you can use environment variables, though this is less secure:

```bash
export LAMIN_CURRENT_INSTANCE="laminlabs/lamindata"
export LAMIN_API_KEY="your-lamin-api-key"
export LAMIN_CURRENT_PROJECT="proj123456789012"  # Project UID
```

### Advanced Configuration

The plugin offers advanced settings for custom deployments or for tuning its behavior.

```groovy
lamin {
  // ... basic settings ...

  // Root-level UIDs apply to all artifacts, runs, and transforms
  project_uids = ['proj123456789012']
  ulabel_uids = ['ulab123456789012']

  // The environment name in LaminDB (e.g. "prod" or "staging")
  env = "prod"
  // Enable dry-run mode to test configuration without creating records
  dry_run = false

  // API connection settings
  api {
    // The Supabase API URL for the LaminDB instance (if env is set to "custom")
    supabase_api_url = "https://your-supabase-api-url.supabase.co"
    // The Supabase anon key for the LaminDB instance (if env is set to "custom")
    supabase_anon_key = secrets.SUPABASE_ANON_KEY
    // The number of retries for API requests
    max_retries = 3
    // The delay between retries in milliseconds
    retry_delay = 100
  }

  // Run-specific metadata linking
  run {
    project_uids = ['proj-run-specific']
    ulabel_uids = ['ulab-run-specific']
  }

  // Transform-specific metadata linking
  transform {
    project_uids = ['proj-transform-specific']
    ulabel_uids = ['ulab-transform-specific']
  }

  // Manually specify a transform UID if known (advanced users only)
  transform_uid = "your-transform-uid"
  // Manually specify a run UID if known (advanced users only)
  run_uid = "your-run-uid"
}
```

### Artifact Tracking Configuration

Control which files are tracked as artifacts and attach metadata using pattern-based rules. You can configure tracking globally, or separately for inputs and outputs.

#### Basic Artifact Tracking

```groovy
lamin {
  // ... instance and api_key ...

  // Control output artifact tracking
  output_artifacts {
    enabled = true
    include_pattern = '.*\\.(fastq|bam|vcf)\\.gz$'  // Only track compressed files
    exclude_pattern = '.*\\.tmp$'                   // Exclude temporary files
  }

  // Control input artifact tracking
  input_artifacts {
    enabled = true
    exclude_pattern = '.*\\.log$'  // Don't track log files as inputs
  }
}
```

#### Advanced Artifact Tracking with Rules

Use rules to apply different configurations based on file patterns:

```groovy
lamin {
  // Root-level UIDs apply to all artifacts, runs, and transforms
  project_uids = ['global-project']
  ulabel_uids = ['project-wide-label']

  // Global artifact settings
  artifacts {
    enabled = true
  }

  // Input-specific configuration
  input_artifacts {
    enabled = true

    rules {
      reference_data {
        pattern = '.*reference.*\\.(fasta|gtf)$'
        ulabel_uids = ['reference-data-label']
        kind = 'reference'
        order = 1  // Higher priority
      }

      sample_data {
        pattern = '.*\\.fastq\\.gz$'
        ulabel_uids = ['raw-reads-label']
        project_uids = ['sequencing-project']
        kind = 'dataset'
        order = 2
      }
    }
  }

  // Output-specific configuration
  output_artifacts {
    enabled = true
    exclude_pattern = '.*\\.(log|tmp)$'  // Exclude logs and temp files

    rules {
      // Exclude work-in-progress outputs
      exclude_intermediate {
        type = 'exclude'
        pattern = '.*intermediate.*'
        order = 1
      }

      // Track final BAM files
      aligned_reads {
        pattern = '.*\\.bam$'
        ulabel_uids = ['aligned-reads-label']
        kind = 'aligned_data'
        order = 2
      }

      // Track variant calls with high priority
      variants {
        pattern = '.*\\.vcf\\.gz$'
        ulabel_uids = ['variants-label']
        project_uids = ['variant-calling-project']
        kind = 'variants'
        order = 3
      }

      // Disable tracking for specific file types
      disable_fastqc {
        enabled = false
        pattern = '.*_fastqc\\.(html|zip)$'
      }
    }
  }
}
```

#### Configuration Options

**Global Options** (apply to `artifacts`, `input_artifacts`, `output_artifacts`):

- `enabled` (Boolean, default: `true`) - Enable or disable artifact tracking
- `include_local` (Boolean, default: `true`) - Whether to track local (`file://`) artifacts. Set to `false` to skip local files entirely
- `exclude_work_dir` (Boolean, default: `true`) - Only for input artifacts. Whether to exclude artifacts located in the Nextflow work directory. This prevents intermediate files between processes from being tracked, which is especially important when the work directory is on a remote filesystem (S3, GCS)
- `exclude_assets_dir` (Boolean, default: `true`) - Only for input artifacts. Whether to exclude artifacts located in `~/.nextflow/assets`. Pipeline source files downloaded by Nextflow live here
- `include_pattern` (String) - Java regex pattern. Files must match this pattern to be tracked
- `exclude_pattern` (String) - Java regex pattern. Files matching this pattern will not be tracked
- `ulabel_uids` (List<String> or String) - ULabel UIDs to attach to all matching artifacts
- `project_uids` (List<String> or String) - Project UIDs to attach to all matching artifacts
- `kind` (String) - Artifact kind (e.g., 'dataset', 'model', 'reference', 'report')
- `rules` (Map) - Named rules for path-specific configurations

**Rule Options** (apply to individual rules within `rules`):

- `enabled` (Boolean, default: `true`) - Enable or disable this rule
- `pattern` (String, **required**) - Java regex pattern to match file paths
- `type` (String, default: `'include'`) - Rule type: `'include'` to track matching files, `'exclude'` to skip them
- `direction` (String, default: inherited) - Apply rule to `'input'`, `'output'`, or `'both'`
- `order` (Integer, default: `100`) - Rule evaluation priority (lower numbers = higher priority)
- `ulabel_uids` (List<String> or String) - ULabel UIDs to attach to matching artifacts
- `project_uids` (List<String> or String) - Project UIDs to attach to matching artifacts
- `kind` (String) - Override artifact kind for matching files

#### Rule Evaluation

1. **Global patterns** are checked first (`include_pattern` and `exclude_pattern`)
2. **Rules** are evaluated in order of priority (`order` field, lower numbers first)
3. **All matching rules** are processed - each can modify the tracking decision and add metadata
4. Later `include` rules can override earlier `exclude` rules, and vice versa
5. If no rules match, the file is tracked using global settings
6. **Metadata merging**: ULabels and Projects from global config and all matching rules are combined (duplicates removed)

#### Pattern Syntax

Patterns use Java regular expressions. Common patterns:

- `.*\\.fastq$` - Match files ending with `.fastq`
- `.*\\.fastq\\.gz$` - Match compressed FASTQ files
- `.*/output/.*` - Match files in any `output` directory
- `.*_(R1|R2)_.*` - Match paired-end read files
- `^(?!.*temp).*$` - Match files NOT containing "temp"

**Important**: Backslashes must be escaped in Groovy strings: `\\.` instead of `\.`

#### Examples

**Disable all artifact tracking:**

```groovy
lamin {
  output_artifacts {
    enabled = false
  }
  input_artifacts {
    enabled = false
  }
}
```

**Track only specific file types:**

```groovy
lamin {
  output_artifacts {
    enabled = true
    include_pattern = '.*\\.(bam|vcf\\.gz|h5ad)$'
  }
}
```

**Exclude temporary and intermediate files:**

```groovy
lamin {
  output_artifacts {
    enabled = true
    exclude_pattern = '.*\\.(tmp|temp|intermediate).*'
  }
}
```

**Different labels for different file types:**

```groovy
lamin {
  output_artifacts {
    rules {
      raw_data {
        pattern = '.*\\.fastq\\.gz$'
        ulabel_uids = ['raw-sequencing-data']
        kind = 'raw_reads'
      }
      processed_data {
        pattern = '.*\\.h5ad$'
        ulabel_uids = ['processed-expression-matrix']
        kind = 'expression_matrix'
      }
    }
  }
}
```

You can also set these using environment variables:

```bash
export LAMIN_ENV="prod"
export LAMIN_DRY_RUN="false"
export LAMIN_CURRENT_PROJECT="proj123456789012"  # Used for project_uids
export SUPABASE_API_URL="https://your-supabase-api-url.supabase.co"
export SUPABASE_ANON_KEY="your-supabase-anon-key"
export LAMIN_MAX_RETRIES=3
export LAMIN_RETRY_DELAY=100
export LAMIN_TRANSFORM_UID="your-transform-uid"
export LAMIN_RUN_UID="your-run-uid"
```

**Advanced settings explained:**

- `project_uids` & `ulabel_uids`: Root-level UIDs that apply to all artifacts, runs, and transforms. Can be combined with object-specific UIDs in `run` and `transform` sections.
- `run` & `transform`: Object-specific metadata linking. UIDs specified here are merged with root-level UIDs.
- `env`: Environment selector for LaminDB instance (e.g., "prod", "staging", or "custom")
- `dry_run`: When `true`, the plugin validates configuration and connects to LaminDB but does not create or modify any records (transforms, runs, or artifacts). Useful for testing your setup without affecting the database.
- `api`: Advanced API connection settings including Supabase connection details and retry behavior
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
