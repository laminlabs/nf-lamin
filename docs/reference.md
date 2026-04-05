# Nextflow: `nf-lamin`

`nf-lamin` is a [Nextflow](https://www.nextflow.io/) plugin that records data lineage for your workflows in [LaminHub](https://lamin.ai/). Without modifying pipeline code, it tracks transforms, runs, and artifacts.

## Quickstart

**1.** Store your [Lamin API key](https://lamin.ai/settings) as a Nextflow secret:

```bash
nextflow secrets set LAMIN_API_KEY <your-lamin-api-key>
```

**2.** Add the plugin to your `nextflow.config`:

```groovy
plugins {
  id 'nf-lamin'
}

lamin {
  instance = "your-org/your-instance"
  api_key = secrets.LAMIN_API_KEY
}
```

**3.** Run your pipeline:

```bash
nextflow run <your-pipeline>
```

## Version compatibility

| nf-lamin  | LaminDB       | Nextflow   | Status         | Key Features                                                      |
| --------- | ------------- | ---------- | -------------- | ----------------------------------------------------------------- |
| **0.6.1** | >= 2.0        | >= 25.10.0 | ✅ Supported   | Fix for edge case in tracking output artifacts                    |
| **0.6.0** | >= 2.0        | >= 25.10.0 | ✅ Supported   | Relativize keys, specify artifact paths, support space and branch |
| 0.5.1     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Track local input files, exclude work and assets directories      |
| 0.5.0     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Improved config, artifact tracking rules, metadata tagging        |
| 0.4.0     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Input artifact tracking                                           |
| 0.3.0     | >= 2.0        | >= 25.04.0 | ❌ Unsupported | Upgrade to LaminDB v2, `lamin://` URI support                     |
| 0.2.x     | >= 1.0, < 2.0 | >= 25.04.0 | ❌ Unsupported | Reports, `getRunUid()`, `getTransformUid()`, `getInstanceSlug()`  |
| 0.1.0     | >= 1.0, < 2.0 | >= 24.04.0 | ❌ Unsupported | Transform & Run tracking, output artifact registration            |

```{toctree}
:maxdepth: 1
:caption: Reference
:hidden:

reference/config
reference/functions
reference/lamin-uri
reference/examples
```
