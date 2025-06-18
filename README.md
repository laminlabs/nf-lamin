# nf-lamin plugin

A Nextflow plugin that integrates
[LaminDB](https://github.com/laminlabs/lamindb) data provenance into
Nextflow workflows.

## Installation

To install a development build of the plugin, run the following command:

```bash
make install
```

To uninstall the plugin, run the following command:

```bash
make uninstall
```

## Usage

To use the plugin in a Nextflow workflow, create a Nextflow config
`nextflow.config` that specifies which LaminDB instance to use and an
API key:

```groovy
plugins {
  id 'nf-lamin'
}

lamin {
  instance = "laminlabs/lamindata"
  api_key = secrets.LAMIN_API_KEY
}
```

> [!TIP]
>
> If you haven’t done so already, you’ll need to define the
> `LAMIN_API_KEY` secret in your Nextflow configuration:
>
> - Fetch a Lamin API key from your [Lamin Hub account
>   settings](https://lamin.ai/settings).
> - Run the command `nextflow secrets set LAMIN_API_KEY "..."` to set
>   the secret in your Nextflow configuration.

### Example with nf-core/scrnaseq workflow:

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

<details>

<summary>

Detailed logs produced by `nf-lamin` in `.nextflow.log`

</summary>

    DEBUG nextflow.lamin.hub.LaminHub - Fetching access token...
    DEBUG nextflow.lamin.hub.LaminHub - Access token refreshed successfully.
    INFO  nextflow.lamin.LaminObserver - ✅ Connected to LaminDB instance 'laminlabs/lamindata' as 'rcannood'
    DEBUG nextflow.lamin.LaminObserver - Searching for existing Transform with key https://github.com/nf-core/scrnaseq and revision 2.7.1
    DEBUG nextflow.lamin.LaminObserver - Found 1 existing Transform(s) with key https://github.com/nf-core/scrnaseq and revision 2.7.1
    INFO  nextflow.lamin.LaminObserver - Transform J49HdErpEFrs0000 (https://staging.laminhub.com/laminlabs/lamindata/transform/J49HdErpEFrs0000)
    INFO  nextflow.lamin.LaminObserver - Run rezkYti2Js3iLPsIlxko (https://staging.laminhub.com/laminlabs/lamindata/transform/J49HdErpEFrs0000/rezkYti2Js3iLPsIlxko)
    Jun-18 14:35:47.837 [PublishDir-3] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/fastqc/Sample_X_1_fastqc.zip
    Jun-18 14:35:47.859 [PublishDir-4] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/fastqc/Sample_X_2_fastqc.zip
    Jun-18 14:35:47.883 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/fastqc/Sample_X_1_fastqc.html
    Jun-18 14:35:47.980 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/fastqc/Sample_X_2_fastqc.html
    Jun-18 14:35:48.385 [PublishDir-9] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/fastqc/Sample_Y_1_fastqc.zip
    Jun-18 14:35:48.404 [PublishDir-10] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/fastqc/Sample_Y_2_fastqc.zip
    Jun-18 14:35:48.555 [PublishDir-6] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/fastqc/Sample_Y_2_fastqc.html
    Jun-18 14:35:48.753 [PublishDir-5] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/fastqc/Sample_Y_1_fastqc.html
    Jun-18 14:35:48.801 [PublishDir-7] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/fastqc/Sample_Y_3_fastqc.html
    Jun-18 14:35:48.847 [PublishDir-8] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/fastqc/Sample_Y_4_fastqc.html
    Jun-18 14:36:12.757 [PublishDir-3] DEBUG nextflow.lamin.LaminObserver - Created output artifact 0MGmGpVV2nAFEE0K0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/0MGmGpVV2nAFEE0K0000)
    Jun-18 14:36:13.275 [PublishDir-3] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/fastqc/Sample_Y_3_fastqc.zip
    Jun-18 14:36:14.927 [PublishDir-4] DEBUG nextflow.lamin.LaminObserver - Created output artifact cIRl18tETTt8ZI8l0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/cIRl18tETTt8ZI8l0000)
    Jun-18 14:36:15.212 [PublishDir-4] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/fastqc/Sample_Y_4_fastqc.zip
    Jun-18 14:36:17.522 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact RmePbjeLCUsvDgq90000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/RmePbjeLCUsvDgq90000)
    Jun-18 14:36:20.005 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact dI7DhPcCmMCYQMqV0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/dI7DhPcCmMCYQMqV0000)
    Jun-18 14:36:20.244 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevin/versions.yml
    Jun-18 14:36:22.029 [PublishDir-9] DEBUG nextflow.lamin.LaminObserver - Created output artifact F8EGf5iOBOK4ceyh0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/F8EGf5iOBOK4ceyh0000)
    Jun-18 14:36:22.291 [PublishDir-9] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevin/mtx_conversions/Sample_X/Sample_X_raw_matrix.rds
    Jun-18 14:36:23.314 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevin/Sample_X_alevin_results
    Jun-18 14:36:24.638 [PublishDir-10] DEBUG nextflow.lamin.LaminObserver - Created output artifact mCpDYSBz5YgL60740000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/mCpDYSBz5YgL60740000)
    Jun-18 14:36:24.833 [PublishDir-10] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevin/mtx_conversions/versions.yml
    Jun-18 14:36:26.787 [PublishDir-6] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact PQchyL3RJjF1C0aY0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/PQchyL3RJjF1C0aY0000)
    Jun-18 14:36:28.894 [PublishDir-5] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact dMTp6F9wbwHlpnlC0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/dMTp6F9wbwHlpnlC0000)
    Jun-18 14:36:29.271 [PublishDir-5] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevin/versions.yml
    Jun-18 14:36:31.446 [PublishDir-7] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact vpoSCeSnm3EXX0Oa0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/vpoSCeSnm3EXX0Oa0000)
    Jun-18 14:36:31.755 [PublishDir-7] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevin/mtx_conversions/Sample_X/Sample_X_raw_matrix.h5ad
    Jun-18 14:36:32.604 [PublishDir-6] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevin/Sample_Y_alevin_results
    Jun-18 14:36:33.546 [PublishDir-8] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact rBwV7EuDA2xDmUCA0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/rBwV7EuDA2xDmUCA0000)
    Jun-18 14:36:33.892 [PublishDir-8] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevin/mtx_conversions/versions.yml
    Jun-18 14:36:35.821 [PublishDir-3] DEBUG nextflow.lamin.LaminObserver - Created output artifact klAaBhxKw4oa77va0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/klAaBhxKw4oa77va0000)
    Jun-18 14:36:36.118 [PublishDir-3] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevin/mtx_conversions/Sample_Y/Sample_Y_raw_matrix.h5ad
    Jun-18 14:36:38.400 [PublishDir-4] DEBUG nextflow.lamin.LaminObserver - Created output artifact Rne5wHGqHEq48Izk0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/Rne5wHGqHEq48Izk0000)
    Jun-18 14:36:38.729 [PublishDir-4] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevin/mtx_conversions/versions.yml
    Jun-18 14:36:40.548 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact 56BoSg5Ct2LI5qWP0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/56BoSg5Ct2LI5qWP0000)
    Jun-18 14:36:40.816 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevin/mtx_conversions/Sample_Y/Sample_Y_raw_matrix.rds
    Jun-18 14:36:42.707 [PublishDir-9] DEBUG nextflow.lamin.LaminObserver - Created output artifact suDHBEeuCJ7teQc80000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/suDHBEeuCJ7teQc80000)
    Jun-18 14:36:43.035 [PublishDir-9] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevin/mtx_conversions/versions.yml
    Jun-18 14:36:45.013 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Created output artifact 55HS8xm11KNbuSp20000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/55HS8xm11KNbuSp20000)
    Jun-18 14:36:45.425 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevin/mtx_conversions/combined_raw_matrix.h5ad
    Jun-18 14:36:47.955 [PublishDir-10] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact vsxSvJTZWpaEXkAn0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/vsxSvJTZWpaEXkAn0000)
    Jun-18 14:36:48.495 [PublishDir-10] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevinqc/alevin_report_Sample_X.html
    Jun-18 14:36:50.164 [PublishDir-5] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact 56BoSg5Ct2LI5qWP0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/56BoSg5Ct2LI5qWP0000)
    Jun-18 14:36:50.690 [PublishDir-5] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/alevinqc/alevin_report_Sample_Y.html
    Jun-18 14:36:52.161 [PublishDir-7] DEBUG nextflow.lamin.LaminObserver - Created output artifact DL5jl1aUYnxp6a7K0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/DL5jl1aUYnxp6a7K0000)
    Jun-18 14:36:53.414 [PublishDir-7] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/multiqc/multiqc_report.html
    Jun-18 14:36:54.242 [PublishDir-6] DEBUG nextflow.lamin.LaminObserver - Created output artifact BDJ5ALk26ANRrDyh0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/BDJ5ALk26ANRrDyh0000)
    Jun-18 14:36:56.308 [PublishDir-6] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/multiqc/multiqc_data
    Jun-18 14:36:57.410 [PublishDir-8] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact vsxSvJTZWpaEXkAn0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/vsxSvJTZWpaEXkAn0000)
    Jun-18 14:36:59.392 [PublishDir-3] DEBUG nextflow.lamin.LaminObserver - Created output artifact 58asQKv1f2yIZSYF0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/58asQKv1f2yIZSYF0000)
    Jun-18 14:37:01.505 [PublishDir-4] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact vsxSvJTZWpaEXkAn0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/vsxSvJTZWpaEXkAn0000)
    Jun-18 14:37:02.575 [PublishDir-8] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 610 at gs://di-temporary-public/scratch/temp-scrnaseq/run_20250618_143527/multiqc/multiqc_plots
    Jun-18 14:37:03.552 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Created output artifact tM5j1jNjAqtzFQSI0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/tM5j1jNjAqtzFQSI0000)
    Jun-18 14:37:05.774 [PublishDir-9] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact vsxSvJTZWpaEXkAn0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/vsxSvJTZWpaEXkAn0000)
    Jun-18 14:37:07.738 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Created output artifact GMlCOFZvtYWvLGir0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/GMlCOFZvtYWvLGir0000)
    Jun-18 14:37:11.120 [PublishDir-10] DEBUG nextflow.lamin.LaminObserver - Created output artifact 8iAXcGbG2iEo0gGQ0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/8iAXcGbG2iEo0gGQ0000)
    Jun-18 14:37:13.092 [PublishDir-5] DEBUG nextflow.lamin.LaminObserver - Created output artifact 4RkkmqoyqFYKWo670000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/4RkkmqoyqFYKWo670000)
    Jun-18 14:37:15.089 [PublishDir-7] DEBUG nextflow.lamin.LaminObserver - Created output artifact ErDqASfz7sSO9t5W0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/ErDqASfz7sSO9t5W0000)
    Jun-18 14:37:17.365 [PublishDir-6] DEBUG nextflow.lamin.LaminObserver - Created output artifact eT1645atKw08Yv9S0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/eT1645atKw08Yv9S0000)
    Jun-18 14:37:19.569 [PublishDir-8] DEBUG nextflow.lamin.LaminObserver - Created output artifact E2prpm1hTc6z1pEx0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/E2prpm1hTc6z1pEx0000)

</details>

### Example with Viash Hub workflow:

```bash
date > /tmp/lamin-test.txt

nextflow run https://packages.viash-hub.com/vsh/toolbox.git \
  -revision v0.1.1 \
  -main-script target/nextflow/bgzip/main.nf \
  -profile docker \
  -plugins nf-lamin@0.1.0 \
  -latest \
  --input /tmp/lamin-test.txt \
  --output output.gz \
  --publish_dir gs://di-temporary-public/scratch/temp-nf-lamin/run_$(date +%Y%m%d_%H%M%S)
```

    Nextflow 25.04.4 is available - Please consider updating your version to it
    N E X T F L O W  ~  version 24.10.5
    Pulling vsh/toolbox ...
    Launching `https://packages.viash-hub.com/vsh/toolbox` [clever_sanger] DSL2 - revision: 5cb3251eaf [v0.1.1]
    ✅ Connected to LaminDB instance 'laminlabs/lamindata' as 'rcannood'
    Transform W818bFm1ecyM0002 (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002)
    Run sHQdswpc0NifXuxuk4eh (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002/sHQdswpc0NifXuxuk4eh)
    [6a/c7f17c] Submitted process > bgzip:processWf:bgzip_process (run)
    [a7/bafef5] Submitted process > bgzip:publishFilesSimpleWf:publishFilesProc (run)
    [2f/9b0238] Submitted process > bgzip:publishStatesSimpleWf:publishStatesProc (run)

> [!NOTE]
>
> Replace the `--publish_dir` with a valid path to a cloud bucket for
> this example to work.

<details>

<summary>

Detailed logs produced by `nf-lamin` in `.nextflow.log`

</summary>

    DEBUG nextflow.lamin.hub.LaminHub - Fetching access token...
    DEBUG nextflow.lamin.hub.LaminHub - Access token refreshed successfully.
    INFO  nextflow.lamin.LaminObserver - ✅ Connected to LaminDB instance 'laminlabs/lamindata' as 'rcannood'
    DEBUG nextflow.lamin.LaminObserver - Searching for existing Transform with key https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf and revision v0.1.1
    DEBUG nextflow.lamin.LaminObserver - Found 1 existing Transform(s) with key https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf and revision v0.1.1
    INFO  nextflow.lamin.LaminObserver - Transform W818bFm1ecyM0002 (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002)
    INFO  nextflow.lamin.LaminObserver - Run sHQdswpc0NifXuxuk4eh (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002/sHQdswpc0NifXuxuk4eh)
    Jun-18 14:37:32.285 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 611 at gs://di-temporary-public/scratch/temp-nf-lamin/run_20250618_143721/run.bgzip.state.yaml
    Jun-18 14:37:32.288 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 611 at gs://di-temporary-public/scratch/temp-nf-lamin/run_20250618_143721/output.gz
    Jun-18 14:37:34.400 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact jAxjm3Kj39aiPUW30000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/jAxjm3Kj39aiPUW30000)
    Jun-18 14:37:36.526 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Created output artifact XD2MDiPezRvhHTjL0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/XD2MDiPezRvhHTjL0000)

</details>

## Settings

The plugin supports the following settings in the `nextflow.config`
file:

```groovy
lamin {
  // The LaminDB instance to use
  instance = "laminlabs/lamindata"
  // The API key for the LaminDB instance
  api_key = secrets.LAMIN_API_KEY
  // The project name in LaminDB
  project = "your-lamin-project"
  // The environment name in LaminDB
  env = "prod"
}
```

In addition to the above settings, you can also set the following
environment variables:

```bash
export LAMIN_CURRENT_INSTANCE="laminlabs/lamindata"
export LAMIN_API_KEY="your-lamin-api-key"
export LAMIN_CURRENT_PROJECT="your-lamin-project"
export LAMIN_ENV="prod"
```

## Advanced settings

There are additional advanced settings that can be configured in the `nextflow.config` file to customize the behavior of the plugin:

```groovy
lamin {
  // The Supabase API URL for the LaminDB instance (if env is set to "custom")
  supabase_api_url = "https://your-supabase-api-url.supabase.co"
  // The Supabase anon key for the LaminDB instance (if env is set to "custom")
  supabase_anon_key = secrets.SUPABASE_ANON_KEY
  // The number of retries for API requests
  max_retries = 3
  // The delay between retries in milliseconds
  retry_delay = 100
}
```

You can also set these advanced settings using environment variables:

```bash
export SUPABASE_API_URL="https://your-supabase-api-url.supabase.co"
export SUPABASE_ANON_KEY="your-supabase-anon-key"
export LAMIN_MAX_RETRIES=3
export LAMIN_RETRY_DELAY=100
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about
contributing to this repository.
