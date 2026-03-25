# Nextflow config

All `nf-lamin` configuration lives in the `lamin {}` scope of your `nextflow.config`.

## Best-practice config

A recommended starting point for nf-core-style pipelines:

```groovy
plugins {
  id 'nf-lamin'
}

lamin {
  instance = "your-org/your-instance"
  api_key = secrets.LAMIN_API_KEY

  // Link all artifacts, runs, and transforms to a project
  project_uids = ['projXXXXXXXXXXXXXX']

  // Track input artifacts
  input_artifacts {
    rules {
      fastq_reads { pattern = '.*\\.fastq(\\.gz)?$'; kind = 'dataset' }
      reference   { pattern = '.*\\.(fasta|fa)(\\.gz)?$'; kind = 'dataset' }
      annotation  { pattern = '.*\\.(gtf|gff)(\\.gz)?$'; kind = 'dataset' }
    }
  }

  // Track output artifacts, stripping the outdir prefix from keys
  output_artifacts {
    key = [relativize: params.outdir]
    rules {
      mapped_reads { pattern = '.*\\.bam$'; kind = 'dataset' }
      reports { pattern = '.*\\.html$'; kind = 'report' }
    }
  }
}
```

The sections below document each setting in detail.

---

## `lamin` - top-level settings

| Setting         | Type    | Default        | Env variable             | Description                                |
| --------------- | ------- | -------------- | ------------------------ | ------------------------------------------ |
| `instance`      | String  | **(required)** | `LAMIN_CURRENT_INSTANCE` | LaminDB instance (`owner/name`)            |
| `api_key`       | String  | **(required)** | `LAMIN_API_KEY`          | Lamin Hub API key (use `nextflow secrets`) |
| `project_uids`  | List    | `null`         | `LAMIN_CURRENT_PROJECT`  | Project UIDs to link to all records        |
| `ulabel_uids`   | List    | `null`         |                          | ULabel UIDs to link to all records         |
| `space_uid`     | String  | `null`         |                          | Space UID                                  |
| `branch_uid`    | String  | `null`         |                          | Branch UID                                 |
| `env`           | String  | `'prod'`       | `LAMIN_ENV`              | Environment (`'prod'` or `'staging'`)      |
| `dry_run`       | Boolean | `false`        | `LAMIN_DRY_RUN`          | Validate config without creating records   |
| `transform_uid` | String  | `null`         | `LAMIN_TRANSFORM_UID`    | Override the auto-generated transform UID  |
| `run_uid`       | String  | `null`         | `LAMIN_RUN_UID`          | Override the auto-generated run UID        |

**Experimental**: UID fields (`project_uids`, `ulabel_uids`, `space_uid`, `branch_uid`) also accept named references: `'?name'` (lookup by name), `'!name'` (lookup, error if missing), `'+name'` (create if missing). This is an experimental feature and may be removed in a future release.

---

## `lamin.run` / `lamin.transform` - record-specific metadata

Attach ULabel UIDs specifically to runs or transforms. These are merged with the root-level `ulabel_uids`.

| Setting       | Type | Default |
| ------------- | ---- | ------- |
| `ulabel_uids` | List | `[]`    |

```groovy
lamin {
  run       { ulabel_uids = ['ulab-run-specific'] }
  transform { ulabel_uids = ['ulab-transform-specific'] }
}
```

---

## `lamin.api` - API connection

| Setting             | Type    | Default | Env variable        |
| ------------------- | ------- | ------- | ------------------- |
| `supabase_api_url`  | String  | `null`  | `SUPABASE_API_URL`  |
| `supabase_anon_key` | String  | `null`  | `SUPABASE_ANON_KEY` |
| `max_retries`       | Integer | `3`     | `LAMIN_MAX_RETRIES` |
| `retry_delay`       | Integer | `100`   | `LAMIN_RETRY_DELAY` |

Only needed for custom LaminHub deployments or to tune retry behavior.

---

## Artifact tracking

Control which files are tracked and what metadata is attached. Configure tracking either globally (`artifacts`) or separately for inputs and outputs (`input_artifacts` / `output_artifacts`). These two approaches are **mutually exclusive**.

### Artifact config options

Apply to `artifacts`, `input_artifacts`, or `output_artifacts`:

| Setting              | Type                   | Default | Description                                      |
| -------------------- | ---------------------- | ------- | ------------------------------------------------ |
| `enabled`            | Boolean                | `true`  | Enable/disable tracking                          |
| `include_local`      | Boolean                | `true`  | Track local (`file://`) artifacts                |
| `exclude_work_dir`   | Boolean                | `true`  | Skip Nextflow work dir (input artifacts only)    |
| `exclude_assets_dir` | Boolean                | `true`  | Skip `~/.nextflow/assets` (input artifacts only) |
| `include_pattern`    | String                 | `null`  | Regex; only matching files are tracked           |
| `exclude_pattern`    | String                 | `null`  | Regex; matching files are skipped                |
| `ulabel_uids`        | List                   | `null`  | ULabel UIDs for matched artifacts                |
| `kind`               | String                 | `null`  | Artifact kind (e.g. `'dataset'`, `'report'`)     |
| `key`                | String / Closure / Map | `null`  | How to derive artifact keys (see below)          |
| `rules`              | Map                    | `{}`    | Pattern-based rules (see below)                  |

### Key derivation

The `key` option controls how artifact keys are generated from file paths. By default, the basename is used.

**Map shorthand** (recommended for nf-core pipelines):

```groovy
key = [relativize: params.outdir]
// /home/user/results/multiqc/report.html → multiqc/report.html
```

**String template** with variables:

- `{basename}`: filename with extension
- `{filename}`: filename without extension
- `{ext}`: extension including dot
- `{parent}`: parent directory name (`{parent.parent}` for grandparent, etc.)

```groovy
key = '{parent}/{basename}'
```

**Closure** for full control:

```groovy
key = { path -> "${path.parent.fileName}/${path.fileName}" }
```

Falls back to basename if resolution fails.

### Rules

Rules apply different settings based on file patterns. Each rule is a named block:

| Setting       | Type                   | Default        | Description                        |
| ------------- | ---------------------- | -------------- | ---------------------------------- |
| `pattern`     | String                 | **(required)** | Java regex to match file paths     |
| `enabled`     | Boolean                | `true`         | Enable/disable this rule           |
| `type`        | String                 | `'include'`    | `'include'` or `'exclude'`         |
| `direction`   | String                 | `'both'`       | `'input'`, `'output'`, or `'both'` |
| `order`       | Integer                | `100`          | Priority (lower = evaluated first) |
| `ulabel_uids` | List                   | `null`         | ULabel UIDs for matched artifacts  |
| `kind`        | String                 | `null`         | Override artifact kind             |
| `key`         | String / Closure / Map | `null`         | Override key derivation            |

**Evaluation order:**

1. Global `include_pattern` / `exclude_pattern` are checked first
2. Rules are evaluated by `order` (lower first)
3. All matching rules are applied; later rules can override earlier ones
4. ULabel UIDs from all matching rules are merged (deduplicated)

Patterns are Java regular expressions. Backslashes must be escaped in Groovy: `\\.` not `\.`

### Example: direction-specific tracking

```groovy
lamin {
  input_artifacts {
    enabled = true
    rules {
      reference { pattern = '.*\\.(fasta|gtf)$'; kind = 'dataset' }
      fastqs    { pattern = '.*\\.fastq\\.gz$'; kind = 'dataset' }
    }
  }

  output_artifacts {
    enabled = true
    key = [relativize: params.outdir]
    exclude_pattern = '.*\\.(log|tmp)$'
    rules {
      exclude_intermediate { type = 'exclude'; pattern = '.*intermediate.*'; order = 1 }
      bam_files  { pattern = '.*\\.bam$'; kind = 'dataset'; order = 2 }
      vcf_files  { pattern = '.*\\.vcf\\.gz$'; kind = 'dataset'; order = 3 }
    }
  }
}
```

### Example: disable all artifact tracking

```groovy
lamin {
  output_artifacts { enabled = false }
  input_artifacts  { enabled = false }
}
```
