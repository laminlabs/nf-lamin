# nf-lamin plugin

A Nextflow plugin that integrates LaminDB data provenance into Nextflow
workflows.

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
lamin {
  instance = "laminlabs/lamindata"
  api_key = System.getenv("LAMIN_API_KEY")
}
```

You can now use the plugin by adding the following option to the
`nextflow run` command:

```bash
-plugins nf-lamin@0.0.1
```

> [!TIP]
>
> You can fetch your Lamin API key from your [Lamin Hub account
> settings](https://lamin.ai/settings).

### Example with scrnaseq workflow:

```bash
nextflow run nf-core/scrnaseq \
  -latest -resume \
  -plugins nf-lamin@0.0.1 \
  -r 2.7.1 \
  -profile docker \
  --input https://github.com/nf-core/test-datasets/raw/scrnaseq/samplesheet-2-0.csv \
  --fasta https://github.com/nf-core/test-datasets/raw/scrnaseq/reference/GRCm38.p6.genome.chr19.fa \
  --gtf https://github.com/nf-core/test-datasets/raw/scrnaseq/reference/gencode.vM19.annotation.chr19.gtf \
  --protocol 10XV2 \
  --skip_emptydrops \
  --outdir output/scrnaseq
```

### Example with Viash Hub workflow:

```bash
nextflow run https://packages.viash-hub.com/vsh/toolbox.git \
  -revision v0.1.0 \
  -main-script target/nextflow/bgzip/main.nf \
  -profile docker \
  -latest \
  -plugins nf-lamin@0.0.1 \
  --input https://github.com/nf-core/test-datasets/raw/scrnaseq/samplesheet-2-0.csv \
  --output output.gz \
  --publish_dir gs://di-temporary/scratch/temp-nf-lamin
```

    N E X T F L O W   ~  version 24.10.4

    Pulling vsh/toolbox ...
    Launching `https://packages.viash-hub.com/vsh/toolbox` [nauseous_edison] DSL2 - revision: 09c015bdf2 [v0.1.0]

    nf-lamin> onFlowCreate triggered!
    nf-lamin> Fetch or create Transform object:
      trafo = ln.Transform(
        key="https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf",
        version="v0.1.0",
        source_code="https://packages.viash-hub.com/vsh/toolbox@09c015bdf233c4911f469219ebf46b0c51faa734:target/nextflow/bgzip/main.nf",
        type="pipeline",
        reference="https://packages.viash-hub.com/vsh/toolbox",
        reference_type="url",
        description="Block compression/decompression utility"
      )

    nf-lamin> Create Run object:
      run = ln.Run(
        transform=trafo,
        name="nauseous_edison",
        started_at="2025-04-08T13:31:50.020977560+02:00"
      )

    executor >  local (2)
    [ca/4944c7] process > bgzip:processWf:bgzip_process (run)                 [100%] 1 of 1 ✔
    [ee/aeb54c] process > bgzip:publishStatesSimpleWf:publishStatesProc (run) [100%] 1 of 1 ✔

    nf-lamin> onProcessComplete name='bgzip:processWf:bgzip_process (run)' triggered!
    nf-lamin> Create Artifact object:
      artifact = ln.Artifact(
        run=run,
        data="/home/rcannood/.nextflow/assets/vsh/toolbox/target/nextflow/bgzip",
      )
    nf-lamin> Create Artifact object:
      artifact = ln.Artifact(
        run=run,
        data="/home/rcannood/workspace/laminlabs/nf-lamin/work/stage-295860b4-cdd5-4333-a02c-f1b32cc97255/aa/9bdca61d39ca63d20737603be1a33d/samplesheet-2-0.csv",
      )

    nf-lamin> onProcessComplete name='bgzip:publishStatesSimpleWf:publishStatesProc (run)' triggered!
    nf-lamin> Create Artifact object:
      artifact = ln.Artifact(
        run=run,
        data="/home/rcannood/workspace/laminlabs/nf-lamin/work/ca/4944c71832a40b321753396c46f2f8/output.gz",
      )

    nf-lamin> onFilePublish triggered!
    nf-lamin> Create Artifact object:
      artifact = ln.Artifact(
        run=run,
        data="gs://di-temporary/scratch/temp-nf-lamin/output.gz",
      )

    nf-lamin> onFilePublish triggered!
    nf-lamin> Create Artifact object:
      artifact = ln.Artifact(
        run=run,
        data="gs://di-temporary/scratch/temp-nf-lamin/run.bgzip.state.yaml",
      )

    nf-lamin> onFlowComplete triggered!

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about
contributing to this repository.
