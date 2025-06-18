# Nextflow

[Nextflow](https://www.nextflow.io/) is the most widely used workflow
manager in bioinformatics.

This guide shows how to register a Nextflow run with inputs & outputs
for the example of the
[nf-core/scrnaseq](https://nf-co.re/scrnaseq/latest) pipeline by running
a Python script.

The approach could be automated by deploying the script via

1.  a serverless environment trigger (e.g., AWS Lambda)
2.  a [post-run
    script](https://docs.seqera.io/platform/23.4.0/launch/advanced#pre-and-post-run-scripts)
    on the Seqera Platform

<div class="dropdown">

What steps are executed by the nf-core/scrnaseq pipeline?

![](https://github.com/nf-core/scrnaseq/blob/e0ddddbff9d8b8c2421c67ff07449a06f9ca02d2/docs/images/scrnaseq_pipeline_V3.0-metro_clean.png?raw=true)

</div>

## Configure nf-lamin

Create a [Lamin Hub API key](https://lamin.ai/settings) and store it as
a Nextflow secret.

```bash
nextflow secrets set LAMIN_API_KEY <your-lamin-api-key>
```

Next, configure a Nextflow project to use the `nf-lamin` plugin. You can
do this by creating a `nextflow.config` in your project with the
following content:

```groovy
plugins {
  id 'nf-lamin'
}

lamin {
  instance = "<your-lamin-org>/<your-lamin-instance>"
  api_key = secrets.LAMIN_API_KEY
}
```

TIP: You can store these settings in `$HOME/.nextflow/config` to make
them available for all Nextflow runs.

## Run the pipeline

Let’s run the `nf-core/scrnaseq` pipeline on remote input data.

```bash
# the test profile uses all downloaded input files as an input
nextflow run nf-core/scrnaseq \
  -r 2.7.1 \
  -profile docker,test \
  -plugins nf-lamin \
  --outdir gs://di-temporary-public/scratch/temp-scrnaseq/run_$(date +%Y%m%d_%H%M%S)
```

<div class="dropdown">

What is the full command and output when running this command?

```bash
nextflow run nf-core/scrnaseq \
  -latest \
  -r 2.7.1 \
  -profile docker \
  -plugins nf-lamin@0.1.0 \
  --input https://github.com/nf-core/test-datasets/raw/scrnaseq/samplesheet-2-0.csv \
  --fasta https://github.com/nf-core/test-datasets/raw/scrnaseq/reference/GRCm38.p6.genome.chr19.fa \
  --gtf https://github.com/nf-core/test-datasets/raw/scrnaseq/reference/gencode.vM19.annotation.chr19.gtf \
  --protocol 10XV2 \
  --skip_emptydrops \
  --outdir gs://di-temporary-public/scratch/temp-scrnaseq/run_$(date +%Y%m%d_%H%M%S)
```

    Nextflow 25.04.4 is available - Please consider updating your version to it
    N E X T F L O W  ~  version 24.10.5
    Pulling nf-core/scrnaseq ...
    Launching `https://github.com/nf-core/scrnaseq` [trusting_brazil] DSL2 - revision: 4171377f40 [2.7.1]
    ✅ Connected to LaminDB instance 'laminlabs/lamindata' as 'rcannood'
    Transform J49HdErpEFrs0000 (https://staging.laminhub.com/laminlabs/lamindata/transform/J49HdErpEFrs0000)
    Run rezkYti2Js3iLPsIlxko (https://staging.laminhub.com/laminlabs/lamindata/transform/J49HdErpEFrs0000/rezkYti2Js3iLPsIlxko)

    ------------------------------------------------------
                                            ,--./,-.
            ___     __   __   __   ___     /,-._.--~'
      |\ | |__  __ /  ` /  \ |__) |__         }  {
      | \| |       \__, \__/ |  \ |___     \`-._,-`-,
                                            `._,._,'
      nf-core/scrnaseq v2.7.1-g4171377
    ------------------------------------------------------
    Core Nextflow options
      revision       : 2.7.1
      runName        : trusting_brazil
      containerEngine: docker
      launchDir      : /home/rcannood/workspace/laminlabs/nf-lamin
      workDir        : /home/rcannood/workspace/laminlabs/nf-lamin/work
      projectDir     : /home/rcannood/.nextflow/assets/nf-core/scrnaseq
      userName       : rcannood
      profile        : docker
      configFiles    :

    Input/output options
      input          : https://github.com/nf-core/test-datasets/raw/scrnaseq/samplesheet-2-0.csv
      outdir         : gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527

    Mandatory arguments
      protocol       : 10XV2

    Skip Tools
      skip_emptydrops: true

    Reference genome options
      fasta          : https://github.com/nf-core/test-datasets/raw/scrnaseq/reference/GRCm38.p6.genome.chr19.fa
      gtf            : https://github.com/nf-core/test-datasets/raw/scrnaseq/reference/gencode.vM19.annotation.chr19.gtf

    !! Only displaying parameters that differ from the pipeline defaults !!
    ------------------------------------------------------
    If you use nf-core/scrnaseq for your analysis please cite:

    * The pipeline
      https://doi.org/10.5281/zenodo.3568187

    * The nf-core framework
      https://doi.org/10.1038/s41587-020-0439-x

    * Software dependencies
      https://github.com/nf-core/scrnaseq/blob/master/CITATIONS.md
    ------------------------------------------------------
    Staging foreign file: https://github.com/nf-core/test-datasets/raw/scrnaseq/reference/GRCm38.p6.genome.chr19.fa
    [7e/4a0219] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:FASTQC_CHECK:FASTQC (Sample_Y)
    [87/3f7d29] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:FASTQC_CHECK:FASTQC (Sample_X)
    Staging foreign file: https://github.com/nf-core/test-datasets/raw/scrnaseq/reference/gencode.vM19.annotation.chr19.gtf
    [98/904be2] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:GTF_GENE_FILTER (GRCm38.p6.genome.chr19.fa)
    [41/83f012] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:SCRNASEQ_ALEVIN:SIMPLEAF_INDEX (GRCm38.p6.genome.chr19_genes.gtf)
    [04/25a4de] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:SCRNASEQ_ALEVIN:SIMPLEAF_QUANT (Sample_X)
    [eb/c66d2e] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:SCRNASEQ_ALEVIN:SIMPLEAF_QUANT (Sample_Y)
    [bb/85b2f0] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:SCRNASEQ_ALEVIN:ALEVINQC (Sample_X)
    [b0/56661f] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:MTX_CONVERSION:MTX_TO_SEURAT (Sample_X)
    [1c/90ba35] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:MTX_CONVERSION:MTX_TO_H5AD (Sample_X)
    [4a/518bf4] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:SCRNASEQ_ALEVIN:ALEVINQC (Sample_Y)
    [2d/0c3a4d] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:MTX_CONVERSION:MTX_TO_SEURAT (Sample_Y)
    [9c/c141b0] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:MTX_CONVERSION:MTX_TO_H5AD (Sample_Y)
    [70/da65ea] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:MTX_CONVERSION:CONCAT_H5AD (1)
    [9c/0912d0] Submitted process > NFCORE_SCRNASEQ:SCRNASEQ:MULTIQC
    Waiting for file transfers to complete (15 files)
    -[nf-core/scrnaseq] Pipeline completed successfully-

</div>

## View transforms & runs on Lamin Hub

There are multiple ways of exploring the run and resulting output artifacts.

Via Lamin Hub:

- Transform: [J49HdErpEFrs0000](https://staging.laminhub.com/laminlabs/lamindata/transform/J49HdErpEFrs0000)
- Run: [rezkYti2Js3iLPsIlxko](https://staging.laminhub.com/laminlabs/lamindata/transform/J49HdErpEFrs0000/rezkYti2Js3iLPsIlxko)

![](nf_core_scrnaseq_run.png)

Or via the Python package:

```python
import lamindb as ln

ln.Run.get("rezkYti2Js3iLPsIlxko")
```

    Run(uid='rezkYti2Js3iLPsIlxko', name='trusting_brazil', started_at=2025-06-18 12:35:30 UTC, finished_at=2025-06-18 12:37:19 UTC, branch_id=1, space_id=1, transform_id=331, created_by_id=28, created_at=2025-06-18 12:35:33 UTC)
