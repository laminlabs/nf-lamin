# nf-lamin plugin


A Nextflow plugin that integrates
[LaminDB](https://github.com/laminlabs/lamindb) data provenance into
Nextflow workflows.

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

To use the plugin in a Nextflow workflow, create a Nextflow config
`nextflow.config` that specifies which LaminDB instance to use and an
API key:

``` groovy
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
> - Fet your Lamin API key from your [Lamin Hub account
>   settings](https://lamin.ai/settings).
> - Run the command `nextflow secrets set LAMIN_API_KEY "..."` to set
>   the secret in your Nextflow configuration.

### Example with nf-core/scrnaseq workflow:

``` bash
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

``` bash
nextflow run https://packages.viash-hub.com/vsh/toolbox.git \
  -revision v0.1.0 \
  -main-script target/nextflow/bgzip/main.nf \
  -profile docker \
  -plugins nf-lamin@0.0.2 \
  -latest \
  --input https://github.com/nf-core/test-datasets/raw/scrnaseq/samplesheet-2-0.csv \
  --output output.gz \
  --publish_dir gs://di-temporary/scratch/temp-nf-lamin
```

    [33mNextflow 25.04.2 is available - Please consider updating your version to it(B[m
    N E X T F L O W  ~  version 24.10.5
    Pulling vsh/toolbox ...
    Launching `https://packages.viash-hub.com/vsh/toolbox` [festering_meitner] DSL2 - revision: 09c015bdf2 [v0.1.0]
    Connected to Lamin instance: laminlabs/lamindata
    Using Transform https://lamin.ai/laminlabs/lamindata/transform/nEOpuME6mvULz2Nv
    Started https://lamin.ai/laminlabs/lamindata/transform/nEOpuME6mvULz2Nv/run/9MekoJQvtgxNjr9NnP2u
    [5d/9516d8] Submitted process > bgzip:processWf:bgzip_process (run)
    [c1/0ffdd1] Submitted process > bgzip:publishStatesSimpleWf:publishStatesProc (run)

> [!NOTE]
>
> Replace the `--publish_dir` with a valid path to a cloud bucket for
> this example to work.

Logs produced by lamin:

    DEBUG nextflow.lamin.LaminObserver - onFlowCreate triggered!
    DEBUG nextflow.lamin.hub.LaminHub - Fetching access token...
    DEBUG nextflow.lamin.hub.LaminHub - Access token refreshed successfully.
    DEBUG nextflow.lamin.api.LaminInstance - GET getInstanceStatistics
    INFO  nextflow.lamin.LaminObserver - Connected to Lamin instance: laminlabs/lamindata
    DEBUG nextflow.lamin.LaminObserver - Searching for existing Transform with key https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf and revision v0.1.0
    DEBUG nextflow.lamin.api.LaminInstance - POST getRecords: core.transform, filter=[and:[[key:[eq:https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf]], [version:[eq:v0.1.0]]]], limit=50, offset=0
    DEBUG nextflow.lamin.LaminObserver - Found 0 existing Transform(s) with key https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf and revision v0.1.0
    DEBUG nextflow.lamin.api.LaminInstance - PUT createRecord: core.transform, data=[key:https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf, source_code:{"repository":"https://packages.viash-hub.com/vsh/toolbox","main-script":"target/nextflow/bgzip/main.nf","commit-id":"09c015bdf233c4911f469219ebf46b0c51faa734","revision":"v0.1.0"}, version:v0.1.0, type:pipeline, reference:https://packages.viash-hub.com/vsh/toolbox, reference_type:url, description:bgzip: Block compression/decompression utility, is_latest:true]
    INFO  nextflow.lamin.LaminObserver - Using Transform https://lamin.ai/laminlabs/lamindata/transform/nEOpuME6mvULz2Nv
    DEBUG nextflow.lamin.api.LaminInstance - PUT createRecord: core.run, data=[transform_id:470.0, name:festering_meitner, created_at:2025-05-17T12:32:25.158634798+02:00, started_at:2025-05-17T12:32:25.158634798+02:00, _status_code:-1]
    INFO  nextflow.lamin.LaminObserver - Started https://lamin.ai/laminlabs/lamindata/transform/nEOpuME6mvULz2Nv/9MekoJQvtgxNjr9NnP2u
    DEBUG nextflow.lamin.LaminObserver - onFlowComplete triggered!
    DEBUG nextflow.lamin.api.LaminInstance - PATCH updateRecord: core.run, uid=9MekoJQvtgxNjr9NnP2u, data=[finished_at:2025-05-17T12:32:54.003625167+02:00, _status_code:0]

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about
contributing to this repository.
