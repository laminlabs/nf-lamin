# nf-lamin plugin


A Nextflow plugin that integrates LaminDB data provenance into Nextflow
workflows.

## Installation

To install a development build of the plugin, run the following command:

``` bash
make install
```

To uninstall the plugin, run the following command:

``` bash
make uninstall
```

## Usage

To use the plugin in a Nextflow workflow, add the following option to
the `nextflow run` command:

``` bash
-plugins nf-lamin@0.0.1
```

Note that the version number should be replaced with the actual version
of the plugin.

### Example with scrnaseq workflow:

``` bash
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

``` bash
nextflow run https://packages.viash-hub.com/vsh/toolbox.git \
  -revision v0.1.0 \
  -main-script target/nextflow/bgzip/main.nf \
  -profile docker \
  -latest -resume \
  -plugins nf-lamin@0.0.1 \
  --input https://github.com/nf-core/test-datasets/raw/scrnaseq/samplesheet-2-0.csv \
  --output output.gz \
  --publish_dir gs://di-temporary/scratch/temp-nf-lamin
```

     N E X T F L O W   ~  version 24.10.4

    Pulling vsh/toolbox ...
    Launching `https://packages.viash-hub.com/vsh/toolbox` [soggy_lamarr] DSL2 - revision: 09c015bdf2 [v0.1.0]

    nf-lamin> onFlowCreate triggered!
    nf-lamin> Fetch or create Transform object:
      trafo = ln.Transform(
        key="target/nextflow/bgzip/main.nf",
        version="v0.1.0",
        type="pipeline",
        reference="https://packages.viash-hub.com/vsh/toolbox",
        reference_type="url",
        description="Block compression/decompression utility"
      )

    nf-lamin> Create Run object:
      run = ln.Run(
        transform=trafo,
        name="soggy_lamarr",
        started_at="2025-03-19T11:22:11.109312126+01:00"
      )

    [4e/b953a4] process > bgzip:processWf:bgzip_process (run)                 [100%] 1 of 1 ✔
    [37/14c402] process > bgzip:publishStatesSimpleWf:publishStatesProc (run) [100%] 1 of 1 ✔
    nf-lamin> onFilePublish triggered!
    nf-lamin> onFilePublish triggered!
    nf-lamin> Create Artifact object:
      artifact = ln.Artifact(
        run=run,
        data="gs://di-temporary/scratch/temp-nf-lamin/output.gz",
      )

    nf-lamin> Create Artifact object:
      artifact = ln.Artifact(
        run=run,
        data="gs://di-temporary/scratch/temp-nf-lamin/run.bgzip.state.yaml",
      )

    nf-lamin> onFlowComplete triggered!

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about
contributing to this repository.
