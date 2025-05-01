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
> You can fetch your Lamin API key from your [Lamin Hub account
> settings](https://lamin.ai/settings).

### Example with nf-core/scrnaseq workflow:

```bash
nextflow run nf-core/scrnaseq \
  -latest -resume \
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
  --input https://github.com/nf-core/test-datasets/raw/scrnaseq/samplesheet-2-0.csv \
  --output output.gz \
  --publish_dir gs://di-temporary/scratch/temp-nf-lamin
```

    Nextflow 24.10.6 is available - Please consider updating your version to it
    N E X T F L O W  ~  version 24.10.5
    Pulling vsh/toolbox ...
    Launching `https://packages.viash-hub.com/vsh/toolbox` [modest_gautier] DSL2 - revision: 09c015bdf2 [v0.1.0]
    Connected to Lamin instance: laminlabs/lamindata
    [8b/6a2386] Submitted process > bgzip:processWf:bgzip_process (run)
    [26/5cbfdd] Submitted process > bgzip:publishStatesSimpleWf:publishStatesProc (run)

> [!NOTE]
>
> Replace the `--publish_dir` with a valid path to a cloud bucket for
> this example to work.

Logs produced by lamin:

    DEBUG nextflow.lamin.LaminObserver - onFlowCreate triggered!
    DEBUG nextflow.lamin.api.LaminHub - Fetching access token...
    DEBUG nextflow.lamin.api.LaminHub - Access token refreshed successfully.
    INFO  nextflow.lamin.LaminObserver - Connected to Lamin instance: laminlabs/lamindata
    DEBUG nextflow.lamin.LaminObserver - Fetch or create Transform object:
      transform = ln.Transform(
        key="https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf",
        version="v0.1.0",
        source_code='''{"repository":"https://packages.viash-hub.com/vsh/toolbox","main-script":"target/nextflow/bgzip/main.nf","commit-id":"09c015bdf233c4911f469219ebf46b0c51faa734","revision":"v0.1.0"}''',
        type="pipeline",
        reference="https://packages.viash-hub.com/vsh/toolbox",
        reference_type="url",
        description="bgzip: Block compression/decompression utility"
    ).save()

    DEBUG nextflow.lamin.LaminObserver - Create Run object:
      transform = ln.Transform.get("abcdef123456")
      run = ln.Run(
        transform=transform,
        name="modest_gautier",
        created_at="2025-05-01T15:50:53.158377805+02:00",
        started_at="2025-05-01T15:50:53.158377805+02:00",
        reference="https://cloud.seqera.io/...",
        reference_type="url",
        project=...
        created_by=...
    ).save()

    DEBUG nextflow.lamin.LaminObserver - onFlowComplete triggered!
    DEBUG nextflow.lamin.LaminObserver - Finalise Run object:
      run = ln.Run.get("abcdef123456")
      run.finished_at = "2025-05-01T15:51:03.312545607+02:00"

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about
contributing to this repository.
