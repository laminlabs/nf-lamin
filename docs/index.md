```{include} ../README.md
:start-line: 0
:end-line: 1
```

```{toctree}
:maxdepth: 1
:hidden:

getting_started
reference
roadmap
changelog
```

The `nf-lamin` plugin for Nextflow provides seamless integration with [LaminDB](https://github.com/laminlabs/lamindb).
It allows you to automatically track workflow runs, parameters, and input/output data, ensuring reproducibility and providing a complete audit trail for your analyses.

## Example Usage

Here's a quick example of how to use `nf-lamin` with the `nf-core/scrnaseq` pipeline. Fill in the following information in your Nextflow configuration file (`nextflow.config`):

```groovy
plugins {
  id 'nf-lamin'
}

lamin {
  instance = "<your-lamin-org>/<your-lamin-instance>"
  api_key = secrets.LAMIN_API_KEY
}
```

Then, you can run the pipeline with the `nf-lamin` plugin enabled.

```bash
nextflow run nf-core/scrnaseq \
  -r "4.0.0" \
  -profile docker,test \
  -plugins nf-lamin \
  --outdir gs://di-temporary-public/scratch/temp-scrnaseq/run_2025-06-23
```

When you run this command, `nf-lamin` will print links to the `Transform` and `Run` records it creates in Lamin Hub:

```
✅ Connected to LaminDB instance 'laminlabs/lamindata' as 'rcannood'
Transform J49HdErpEFrs0000 (https://staging.laminhub.com/laminlabs/lamindata/transform/J49HdErpEFrs0000)
Run rezkYti2Js3iLPsIlxko (https://staging.laminhub.com/laminlabs/lamindata/transform/J49HdErpEFrs0000/rezkYti2Js3iLPsIlxko)
```

## More information

For detailed documentation on how to use `nf-lamin`, including configuration options and advanced features, please refer to the [Getting Started](getting_started.md) and [Reference](reference.md) sections.
