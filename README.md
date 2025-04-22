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

### Example with nf-core/scrnaseq workflow:

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

    N E X T F L O W  ~  version 24.10.5
    Pulling vsh/toolbox ...
    Launching `https://packages.viash-hub.com/vsh/toolbox` [high_waddington] DSL2 - revision: 09c015bdf2 [v0.1.0]
    Connected to Lamin instance: laminlabs/lamindata
    [6a/7201f1] Submitted process > bgzip:processWf:bgzip_process (run)
    [93/7bfc4d] Submitted process > bgzip:publishStatesSimpleWf:publishStatesProc (run)

Logs produced by lamin:

```bash
gawk '
/\[main\]/ {
  if ($0 ~ /nextflow\.lamin/) {
    do_print=1
  } else {
    do_print=0
  }
}
do_print {
  gsub(/.*\[main\] /, "")
  print
}
' .nextflow.log
```

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
        name="high_waddington",
        created_at="2025-04-22T13:49:18.787752751+02:00",
        started_at="2025-04-22T13:49:18.787752751+02:00",
        reference="https://cloud.seqera.io/...",
        reference_type="url",
        project=...
        created_by=...
    ).save()

    DEBUG nextflow.lamin.LaminObserver - onFlowComplete triggered!
    DEBUG nextflow.lamin.LaminObserver - Finalise Run object:
      run = ln.Run.get("abcdef123456")
      run.finished_at = "2025-04-22T13:49:29.017797592+02:00"

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about
contributing to this repository.
