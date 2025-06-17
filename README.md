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
> - Fetch a Lamin API key from your [Lamin Hub account
>   settings](https://lamin.ai/settings).
> - Run the command `nextflow secrets set LAMIN_API_KEY "..."` to set
>   the secret in your Nextflow configuration.

### Example with nf-core/scrnaseq workflow:

``` bash
nextflow run nf-core/scrnaseq \
  -latest -resume \
  -r 2.7.1 \
  -profile docker \
  -plugins nf-lamin@0.1.0 \
  --input https://github.com/nf-core/test-datasets/raw/scrnaseq/samplesheet-2-0.csv \
  --fasta https://github.com/nf-core/test-datasets/raw/scrnaseq/reference/GRCm38.p6.genome.chr19.fa \
  --gtf https://github.com/nf-core/test-datasets/raw/scrnaseq/reference/gencode.vM19.annotation.chr19.gtf \
  --protocol 10XV2 \
  --skip_emptydrops \
  --publish_dir gs://di-temporary-public/scratch/temp-scrnaseq
```

### Example with Viash Hub workflow:

``` bash
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
    Launching `https://packages.viash-hub.com/vsh/toolbox` [intergalactic_swartz] DSL2 - revision: 5cb3251eaf [v0.1.1]
    ✅ Connected to LaminDB instance 'laminlabs/lamindata' as 'rcannood'
    Transform W818bFm1ecyM0002 (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002)
    Run Bll8txYVVGMm2VwaRrBq (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002/Bll8txYVVGMm2VwaRrBq)
    [d7/b65619] Submitted process > bgzip:processWf:bgzip_process (run)
    [fc/c26677] Submitted process > bgzip:publishFilesSimpleWf:publishFilesProc (run)
    [de/008aea] Submitted process > bgzip:publishStatesSimpleWf:publishStatesProc (run)

> [!NOTE]
>
> Replace the `--publish_dir` with a valid path to a cloud bucket for
> this example to work.

Logs produced by lamin:

    DEBUG nextflow.lamin.LaminObserver - onFlowCreate triggered!
    DEBUG nextflow.lamin.hub.LaminHub - Fetching access token...
    DEBUG nextflow.lamin.hub.LaminHub - Access token refreshed successfully.
    DEBUG nextflow.lamin.instance.Instance - GET /account
    DEBUG nextflow.lamin.instance.Instance - Response from getAccount: [id:b18149dc-7062-4f5e-8f2b-a82c7e13c5ed, lnid:QVOJJSTi, handle:rcannood, user_id:b18149dc-7062-4f5e-8f2b-a82c7e13c5ed, name:null, bio:null, website:null, github_handle:null, twitter_handle:null, linkedin_handle:null, avatar_url:b18149dc-7062-4f5e-8f2b-a82c7e13c5ed/avatar.png, created_at:2025-05-19T07:20:22.157833Z, updated_at:null]
    INFO  nextflow.lamin.LaminObserver - ✅ Connected to LaminDB instance 'laminlabs/lamindata' as 'rcannood'
    DEBUG nextflow.lamin.LaminObserver - Searching for existing Transform with key https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf and revision v0.1.1
    DEBUG nextflow.lamin.instance.Instance - POST getRecords: core.transform, filter=[and:[[key:[eq:https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf]], [version:[eq:v0.1.1]]]], limit=50, offset=0
    DEBUG nextflow.lamin.instance.Instance - Response from getRecords: [[is_latest:true, description:bgzip: Block compression/decompression utility, reference_type:url, hash:HYde-dNrm7hxGkcdzbwkeA, _aux:null, type:pipeline, reference:https://packages.viash-hub.com/vsh/toolbox, updated_at:2025-06-16T14:46:03.982506+00:00, key:https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf, source_code:{"repository":"https://packages.viash-hub.com/vsh/toolbox","main-script":"target/nextflow/bgzip/main.nf","commit-id":"5cb3251eaf4f716fbbf45669b21bb63f95448b6e","revision":"v0.1.1"}, version:v0.1.1, created_at:2025-06-16T14:46:03.982506+00:00, uid:W818bFm1ecyM0002, id:330.0]]
    DEBUG nextflow.lamin.LaminObserver - Found 1 existing Transform(s) with key https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf and revision v0.1.1
    INFO  nextflow.lamin.LaminObserver - Transform W818bFm1ecyM0002 (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002)
    DEBUG nextflow.lamin.instance.Instance - PUT createRecord: core.run, data=[transform_id:330.0, name:intergalactic_swartz, created_at:2025-06-17T09:30:33.121718744+02:00, started_at:2025-06-17T09:30:33.121718744+02:00, _status_code:-1]
    DEBUG nextflow.lamin.instance.Instance - Response from createRecord: [[id:578.0, uid:Bll8txYVVGMm2VwaRrBq, _aux:null, name:intergalactic_swartz, space_id:1.0, _branch_code:1.0, report_id:null, _logfile_id:null, reference:null, transform_id:330.0, created_at:2025-06-17T07:30:35.735546+00:00, created_by_id:28.0, started_at:2025-06-17T07:30:33.121719+00:00, environment_id:null, finished_at:null, _status_code:-1.0, reference_type:null, _is_consecutive:null, initiated_by_run_id:null]]
    INFO  nextflow.lamin.LaminObserver - Run Bll8txYVVGMm2VwaRrBq (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002/Bll8txYVVGMm2VwaRrBq)
    Jun-17 09:30:39.996 [TaskFinalizer-1] DEBUG nextflow.lamin.LaminObserver - onProcessComplete triggered!
    Jun-17 09:30:40.851 [TaskFinalizer-3] DEBUG nextflow.lamin.LaminObserver - onProcessComplete triggered!
    Jun-17 09:30:40.851 [TaskFinalizer-2] DEBUG nextflow.lamin.LaminObserver - onProcessComplete triggered!
    Jun-17 09:30:41.346 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - onFilePublish triggered!
    Jun-17 09:30:41.347 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 578 at gs://di-temporary-public/scratch/temp-nf-lamin/run_20250617_093030/run.bgzip.state.yaml
    Jun-17 09:30:41.348 [PublishDir-2] DEBUG nextflow.lamin.instance.Instance - POST /instances/{instance_id}/artifacts/create: {"path":"gs://di-temporary-public/scratch/temp-nf-lamin/run_20250617_093030/run.bgzip.state.yaml","kwargs":{"run_id":578,"description":"Output artifact for run 578"}}
    Jun-17 09:30:41.362 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - onFilePublish triggered!
    Jun-17 09:30:41.362 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 578 at gs://di-temporary-public/scratch/temp-nf-lamin/run_20250617_093030/output.gz
    Jun-17 09:30:41.362 [PublishDir-1] DEBUG nextflow.lamin.instance.Instance - POST /instances/{instance_id}/artifacts/create: {"path":"gs://di-temporary-public/scratch/temp-nf-lamin/run_20250617_093030/output.gz","kwargs":{"run_id":578,"description":"Output artifact for run 578"}}
    Jun-17 09:30:44.106 [PublishDir-2] DEBUG nextflow.lamin.instance.Instance - Response from createArtifact: [statusCode:200.0, body:[message:Artifact created successfully, artifact:[version:null, is_latest:true, branch:1.0, space:1.0, _aux:null, created_at:2025-06-16T15:22:04.384Z, updated_at:2025-06-16T15:22:04.384Z, uid:jAxjm3Kj39aiPUW30000, key:scratch/temp-nf-lamin/run.bgzip.state.yaml, description:Output artifact for run 578, storage:32.0, suffix:.yaml, kind:null, otype:null, size:35.0, hash:p41ifSZvoMr4+4FoOJBpwA, n_files:null, n_observations:null, _hash_type:md5, run:563.0, schema:null, _key_is_virtual:false, created_by:28.0, _overwrite_versions:false, input_of_runs:[], _subsequent_runs:[], _actions:[]]]]
    Jun-17 09:30:44.107 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact jAxjm3Kj39aiPUW30000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/jAxjm3Kj39aiPUW30000)
    Jun-17 09:30:44.255 [PublishDir-1] DEBUG nextflow.lamin.instance.Instance - Response from createArtifact: [statusCode:200.0, body:[message:Artifact created successfully, artifact:[version:null, is_latest:true, branch:1.0, space:1.0, _aux:null, created_at:2025-06-17T07:30:44.102Z, updated_at:2025-06-17T07:30:44.102Z, uid:9ilgajRJ0x9E6PHL0000, key:scratch/temp-nf-lamin/run_20250617_093030/output.gz, description:Output artifact for run 578, storage:32.0, suffix:.gz, kind:null, otype:null, size:86.0, hash:ZceCLcanTfqvTGn7d0T/jQ, n_files:null, n_observations:null, _hash_type:md5, run:578.0, schema:null, _key_is_virtual:false, created_by:28.0, _overwrite_versions:false, input_of_runs:[], _subsequent_runs:[], _actions:[]]]]
    Jun-17 09:30:44.255 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Created output artifact 9ilgajRJ0x9E6PHL0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/9ilgajRJ0x9E6PHL0000)
    DEBUG nextflow.lamin.LaminObserver - onFlowComplete triggered!
    DEBUG nextflow.lamin.instance.Instance - PATCH updateRecord: core.run, uid=Bll8txYVVGMm2VwaRrBq, data=[finished_at:2025-06-17T09:30:44.256162864+02:00, _status_code:0]
    DEBUG nextflow.lamin.instance.Instance - Response from updateRecord: [id:578.0, uid:Bll8txYVVGMm2VwaRrBq, _aux:null, name:intergalactic_swartz, space_id:1.0, _branch_code:1.0, report_id:null, _logfile_id:null, reference:null, transform_id:330.0, created_at:2025-06-17T07:30:35.735546+00:00, created_by_id:28.0, started_at:2025-06-17T07:30:33.121719+00:00, environment_id:null, finished_at:2025-06-17T07:30:44.256163+00:00, _status_code:0.0, reference_type:null, _is_consecutive:null, initiated_by_run_id:null]

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about
contributing to this repository.
