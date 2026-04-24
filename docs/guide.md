---
execute_via: nbconvert
---

# Nextflow

There are several ways to track Nextflow pipeline runs and artifacts in [LaminDB](https://lamin.ai/).

## Using `nf-lamin` (recommended)

The [`nf-lamin`](https://github.com/laminlabs/nf-lamin) Nextflow plugin automatically tracks transforms, runs, and artifacts without modifying pipeline code. It requires a [LaminHub](https://lamin.ai/) account.

**1.** Store your [Lamin API key](https://lamin.ai/settings) as a Nextflow secret:

```bash tags=["skip-execution"]
nextflow secrets set LAMIN_API_KEY <your-lamin-api-key>
```

**2.** Add the plugin to your `nextflow.config`:

```groovy tags=["skip-execution"]
plugins {
  id 'nf-lamin'
}

lamin {
  instance = "your-org/your-instance"
  api_key = secrets.LAMIN_API_KEY
}
```

**3.** Run your pipeline:

```bash tags=["skip-execution"]
nextflow run <your-pipeline>
```

After the run, explore the tracked data in LaminHub or via the Python SDK:

```python tags=["skip-execution"]
import lamindb as ln

ln.Run.get("your-run-uid")
```

![](guide/nf_core_scrnaseq_run.png)

→ See {doc}`/reference` for the full `nf-lamin` configuration reference.

→ See {doc}`/reference/examples` for ready-to-run examples for existing pipelines.

## Using a post-run script

If you want to use Nextflow with LaminDB but without the `nf-lamin` plugin, you can register runs manually with a Python post-run script.

:::{dropdown} Example: nf-core/scrnaseq post-run registration

![](guide/nf_core_scrnaseq_diagram.png)

After running the pipeline, a Python script registers inputs & outputs in LaminDB:

```{eval-rst}
.. literalinclude:: guide/register_scrnaseq_run.py
   :language: python
   :caption: nf-core/scrnaseq run registration
```

Run it with:

```bash
python register_scrnaseq_run.py --input scrnaseq_input --output scrnaseq_output
```

If need be, such a script can be deployed via a serverless environment trigger (e.g., AWS Lambda).

:::
