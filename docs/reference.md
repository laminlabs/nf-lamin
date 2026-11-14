# Nextflow: `nf-lamin`

`nf-lamin` is a [Nextflow](https://www.nextflow.io/) plugin that records data lineage for your workflows in [LaminHub](https://lamin.ai/). Without modifying pipeline code, it tracks transforms, runs, and artifacts.

## Quickstart

**Option A: environment variables** (no config file needed):

```bash
export LAMIN_CURRENT_INSTANCE="your-org/your-instance"
export LAMIN_API_KEY="<your-lamin-api-key>"
nextflow run -plugins nf-lamin@~1.0.0 <your-pipeline>
```

**Option B: Nextflow secrets + config file**:

Store your API key as a Nextflow secret:

```bash
nextflow secrets set LAMIN_API_KEY <your-lamin-api-key>
```

Create a `lamin.config` file:

```groovy
plugins {
  id 'nf-lamin@~1.0.0'
}

lamin {
  instance = "your-org/your-instance"
  api_key = secrets.LAMIN_API_KEY
}
```

Then run your pipeline with the config:

```bash
nextflow run <your-pipeline> -c lamin.config
```

## Version compatibility

| nf-lamin  | LaminDB       | Nextflow   | Status         | Key Features                                                                    |
| --------- | ------------- | ---------- | -------------- | ------------------------------------------------------------------------------- |
| **1.0.0** | >= 2.0        | >= 25.10.0 | ✅ Supported   | Publish to `lamin://` storage, client-side artifact stats, reliable run status  |
| **0.9.0** | >= 2.0        | >= 25.10.0 | ✅ Supported   | Annotate artifacts, Seqera Platform run url, anonymous `lamin://` resolution    |
| 0.8.3     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Make API interactions configurable and more robust, clearer permission errors   |
| 0.8.2     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Parallellized artifact creation                                                 |
| 0.8.1     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Improved trace logging                                                          |
| 0.8.0     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Support for Nextflow 26.04, fix key resolution, deprecate uploading local paths |
| 0.7.0     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Automatic credential federation for `lamin://` URIs (S3)                        |
| 0.6.2     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Fix deferred param evaluation, documentation improvements                       |
| 0.6.1     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Fix for edge case in tracking output artifacts                                  |
| 0.6.0     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Relativize keys, specify artifact paths, support space and branch               |
| 0.5.1     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Track local input files, exclude work and assets directories                    |
| 0.5.0     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Improved config, artifact tracking rules, metadata tagging                      |
| 0.4.0     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Input artifact tracking                                                         |
| 0.3.0     | >= 2.0        | >= 25.04.0 | ❌ Unsupported | Upgrade to LaminDB v2, `lamin://` URI support                                   |
| 0.2.x     | >= 1.0, < 2.0 | >= 25.04.0 | ❌ Unsupported | Reports, `getRunUid()`, `getTransformUid()`, `getInstanceSlug()`                |
| 0.1.0     | >= 1.0, < 2.0 | >= 24.04.0 | ❌ Unsupported | Transform & Run tracking, output artifact registration                          |

## Stability

From 1.0.0 on, `nf-lamin` follows [semantic versioning](https://semver.org/): the config options, the `lamin://` URI format and the DSL functions only change in a major release. Pin `nf-lamin@~1.0.0` to pick up patch releases automatically.

These parts are experimental and can change in a minor release:

- `lamin-s3://` URIs
- named references in UID settings (`'?name'`, `'!name'`, `'+name'`)
- the `lamin.api` settings
- how new transforms are keyed

Tracking is best effort: a tracking failure is logged as a warning and never fails the pipeline.

```{toctree}
:maxdepth: 1
:caption: Reference
:hidden:

reference/config
reference/functions
reference/lamin-uri
reference/examples
```
