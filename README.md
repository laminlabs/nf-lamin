# nf-lamin

`nf-lamin` is a [Nextflow](https://www.nextflow.io/) plugin that records data provenance for your workflows in [LaminHub](https://lamin.ai/). Without modifying pipeline code, it tracks:

- **Transforms** — the pipeline definition (repository, script, version)
- **Runs** — each execution (parameters, timing, status)
- **Artifacts** — input and output files, linked to the run

## Quick start

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

The plugin prints links to the Transform and Run records it creates:

```
✅ Connected to LaminDB instance 'your-org/your-instance' as 'your-username'
Transform XXXYYYZZZABC0001 (https://lamin.ai/your-org/your-instance/transform/XXXYYYZZZABC0001)
Run abcdefghijklmnopqrst (https://lamin.ai/your-org/your-instance/transform/XXXYYYZZZABC0001/abcdefghijklmnopqrst)
```

## Documentation

For more information, see the [guide](https://docs.lamin.ai/nextflow) and [reference documentation](https://docs.lamin.ai/nf-lamin).

## Version compatibility

| nf-lamin  | LaminDB       | Nextflow   | Status         | Key Features                                                      |
| --------- | ------------- | ---------- | -------------- | ----------------------------------------------------------------- |
| **0.5.2** | >= 2.0        | >= 25.10.0 | ✅ Supported   | Configure artifact keys, manually include artifacts               |
| 0.5.1     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Track local input files, exclude work and assets directories      |
| 0.5.0     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Improved config, artifact tracking rules, metadata tagging        |
| 0.4.0     | >= 2.0        | >= 25.10.0 | ❌ Unsupported | Input artifact tracking                                           |
| 0.3.0     | >= 2.0        | >= 25.04.0 | ❌ Unsupported | Upgrade to LaminDB v2, `lamin://` URI support                     |
| 0.2.x     | >= 1.0, < 2.0 | >= 25.04.0 | ❌ Unsupported | Reports, `getRunUid()`, `getTransformUid()`, `getInstanceSlug()`  |
| 0.1.0     | >= 1.0, < 2.0 | >= 24.04.0 | ❌ Unsupported | Transform & Run tracking, output artifact registration            |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about contributing to this repository.
