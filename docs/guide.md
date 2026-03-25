# Nextflow

```{include} ../README.md
:start-after: # nf-lamin
:end-before: ## Documentation
```

## Artifact tracking

By default, all published output files are tracked as artifacts and linked to the run.

### Artifact keys

For nf-core-style pipelines, strip the output directory prefix to preserve the directory structure in artifact keys:

```groovy
lamin {
  output_artifacts {
    key = [relativize: params.outdir]
  }
}
```

This stores `multiqc/star/multiqc_report.html` as the artifact key instead of just the filename.

### Metadata

Attach projects or labels globally to all artifacts, runs, and transforms:

```groovy
lamin {
  project_uids = ['proj123456789012']
  ulabel_uids = ['ulab123456789012']
}
```

### Filtering

Control which files are tracked using include/exclude patterns:

```groovy
lamin {
  output_artifacts {
    include_pattern = '.*\\.(fastq|bam|vcf)\\.gz$'
    exclude_pattern = '.*\\.tmp$'
  }

  input_artifacts {
    enabled = true
  }
}
```

→ See {doc}`/reference/config` for the full configuration reference.

→ See {doc}`/reference/functions` for DSL functions and {doc}`/reference/lamin-uri` for `lamin://` URI support.

→ See {doc}`/reference/examples` for ready-to-run nf-core/rnaseq and bigbio/quantms configurations.

## Viewing results

After the run, explore the tracked data in LaminHub or via the Python SDK:

```python
import lamindb as ln

ln.Run.get("your-run-uid")
```

![](guide/nf_core_scrnaseq_run.png)

## Manual tracking with a post-run script

If you want to use Nextflow with LaminDB but without [LaminHub](https://lamin.ai), you can register runs manually with a Python post-run script. This is useful for users who want a fully open-source stack without any SaaS dependencies.

Note that this approach does not provide the same automation as `nf-lamin` (real-time run tracking, automatic artifact registration). It also cannot integrate with [Seqera Cloud](https://seqera.io/), which requires the `nf-lamin` plugin.

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

Such a script can be deployed via:

1. A serverless environment trigger (e.g., AWS Lambda)
2. A [post-run script](https://docs.seqera.io/platform-cloud/launch/advanced#pre-and-post-run-scripts) on the Seqera Platform

:::
