# Nextflow: `nf-lamin`

`nf-lamin` is a [Nextflow](https://www.nextflow.io/) plugin that records data provenance for your workflows in [LaminHub](https://lamin.ai/). Without modifying pipeline code, it tracks transforms, runs, and artifacts.

→ See {doc}`guide` for an overview of all approaches to using Nextflow with LaminDB.

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

## Reference

```{toctree}
:maxdepth: 1

reference/config
reference/functions
reference/lamin-uri
reference/examples
```
