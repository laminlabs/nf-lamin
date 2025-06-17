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
    Launching `https://packages.viash-hub.com/vsh/toolbox` [silly_kay] DSL2 - revision: 5cb3251eaf [v0.1.1]
    ✅ Connected to LaminDB instance 'laminlabs/lamindata' as 'rcannood'
    Transform W818bFm1ecyM0002 (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002)
    Run xkKm3HSEc4vHMjh9RRK7 (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002/xkKm3HSEc4vHMjh9RRK7)
    [ca/bd7cd0] Submitted process > bgzip:processWf:bgzip_process (run)
    [d2/7f1ecb] Submitted process > bgzip:publishFilesSimpleWf:publishFilesProc (run)
    [2d/95a01e] Submitted process > bgzip:publishStatesSimpleWf:publishStatesProc (run)
    Waiting for file transfers to complete (2 files)

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
    DEBUG nextflow.lamin.instance.Instance - Response from getRecords: [[type:pipeline, key:https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf, source_code:{"repository":"https://packages.viash-hub.com/vsh/toolbox","main-script":"target/nextflow/bgzip/main.nf","commit-id":"5cb3251eaf4f716fbbf45669b21bb63f95448b6e","revision":"v0.1.1"}, version:v0.1.1, reference:https://packages.viash-hub.com/vsh/toolbox, created_at:2025-06-16T14:46:03.982506+00:00, description:bgzip: Block compression/decompression utility, hash:HYde-dNrm7hxGkcdzbwkeA, uid:W818bFm1ecyM0002, id:330.0, updated_at:2025-06-16T14:46:03.982506+00:00, reference_type:url, is_latest:true, _aux:null]]
    DEBUG nextflow.lamin.LaminObserver - Found 1 existing Transform(s) with key https://packages.viash-hub.com/vsh/toolbox:target/nextflow/bgzip/main.nf and revision v0.1.1
    INFO  nextflow.lamin.LaminObserver - Transform W818bFm1ecyM0002 (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002)
    DEBUG nextflow.lamin.instance.Instance - PUT createRecord: core.run, data=[transform_id:330.0, name:silly_kay, created_at:2025-06-17T16:25:37.408037390+02:00, started_at:2025-06-17T16:25:37.408037390+02:00, _status_code:-1]
    DEBUG nextflow.lamin.instance.Instance - Response from createRecord: [[id:599.0, uid:xkKm3HSEc4vHMjh9RRK7, _aux:null, name:silly_kay, space_id:1.0, _branch_code:1.0, report_id:null, _logfile_id:null, reference:null, transform_id:330.0, created_at:2025-06-17T14:25:40.235964+00:00, created_by_id:28.0, started_at:2025-06-17T14:25:37.408037+00:00, environment_id:null, finished_at:null, _status_code:-1.0, reference_type:null, _is_consecutive:null, initiated_by_run_id:null]]
    INFO  nextflow.lamin.LaminObserver - Run xkKm3HSEc4vHMjh9RRK7 (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002/xkKm3HSEc4vHMjh9RRK7)
    Jun-17 16:25:44.362 [TaskFinalizer-1] DEBUG nextflow.lamin.LaminObserver - onProcessComplete triggered!
    Jun-17 16:25:45.210 [TaskFinalizer-2] DEBUG nextflow.lamin.LaminObserver - onProcessComplete triggered!
    Jun-17 16:25:45.210 [TaskFinalizer-3] DEBUG nextflow.lamin.LaminObserver - onProcessComplete triggered!
    Jun-17 16:25:45.672 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - onFilePublish triggered!
    Jun-17 16:25:45.673 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 599 at gs://di-temporary-public/scratch/temp-nf-lamin/run_20250617_162535/output.gz
    Jun-17 16:25:45.674 [PublishDir-1] DEBUG nextflow.lamin.instance.Instance - POST /instances/{instance_id}/artifacts/create: {"path":"gs://di-temporary-public/scratch/temp-nf-lamin/run_20250617_162535/output.gz","kwargs":{"run_id":599,"description":"Output artifact for run 599"}}
    Jun-17 16:25:45.684 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - onFilePublish triggered!
    Jun-17 16:25:45.684 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 599 at gs://di-temporary-public/scratch/temp-nf-lamin/run_20250617_162535/run.bgzip.state.yaml
    Jun-17 16:25:45.684 [PublishDir-2] DEBUG nextflow.lamin.instance.Instance - POST /instances/{instance_id}/artifacts/create: {"path":"gs://di-temporary-public/scratch/temp-nf-lamin/run_20250617_162535/run.bgzip.state.yaml","kwargs":{"run_id":599,"description":"Output artifact for run 599"}}
    Jun-17 16:26:10.868 [PublishDir-1] DEBUG nextflow.lamin.instance.Instance - Response from createArtifact: [statusCode:200.0, body:[message:Artifact created successfully, artifact:[version:null, is_latest:true, branch:1.0, space:1.0, _aux:null, created_at:2025-06-17T14:26:10.724Z, updated_at:2025-06-17T14:26:10.724Z, uid:0P6tPRXCkBaU9Q8H0000, key:scratch/temp-nf-lamin/run_20250617_162535/output.gz, description:Output artifact for run 599, storage:32.0, suffix:.gz, kind:null, otype:null, size:86.0, hash:ZBfgYI9182T75D1+IigC9Q, n_files:null, n_observations:null, _hash_type:md5, run:599.0, schema:null, _key_is_virtual:false, created_by:28.0, _overwrite_versions:false, input_of_runs:[], _subsequent_runs:[], _actions:[]]]]
    Jun-17 16:26:10.869 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Created output artifact 0P6tPRXCkBaU9Q8H0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/0P6tPRXCkBaU9Q8H0000)
    Jun-17 16:26:11.947 [PublishDir-2] DEBUG nextflow.lamin.instance.Instance - Response from createArtifact: [statusCode:200.0, body:[message:Artifact created successfully, artifact:[version:null, is_latest:true, branch:1.0, space:1.0, _aux:null, created_at:2025-06-16T15:22:04.384Z, updated_at:2025-06-16T15:22:04.384Z, uid:jAxjm3Kj39aiPUW30000, key:scratch/temp-nf-lamin/run.bgzip.state.yaml, description:Output artifact for run 599, storage:32.0, suffix:.yaml, kind:null, otype:null, size:35.0, hash:p41ifSZvoMr4+4FoOJBpwA, n_files:null, n_observations:null, _hash_type:md5, run:563.0, schema:null, _key_is_virtual:false, created_by:28.0, _overwrite_versions:false, input_of_runs:[], _subsequent_runs:[], _actions:[]]]]
    Jun-17 16:26:11.948 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact jAxjm3Kj39aiPUW30000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/jAxjm3Kj39aiPUW30000)
    DEBUG nextflow.lamin.LaminObserver - onFlowComplete triggered!
    DEBUG nextflow.lamin.instance.Instance - PATCH updateRecord: core.run, uid=xkKm3HSEc4vHMjh9RRK7, data=[finished_at:2025-06-17T16:26:11.948616439+02:00, _status_code:0]
    DEBUG nextflow.lamin.instance.Instance - Response from updateRecord: [id:599.0, uid:xkKm3HSEc4vHMjh9RRK7, _aux:null, name:silly_kay, space_id:1.0, _branch_code:1.0, report_id:null, _logfile_id:null, reference:null, transform_id:330.0, created_at:2025-06-17T14:25:40.235964+00:00, created_by_id:28.0, started_at:2025-06-17T14:25:37.408037+00:00, environment_id:null, finished_at:2025-06-17T14:26:11.948616+00:00, _status_code:0.0, reference_type:null, _is_consecutive:null, initiated_by_run_id:null]

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
  // The Supabase API URL for the LaminDB instance (if env is set to null)
  supabase_api_url = "https://your-supabase-api-url.supabase.co"
  // The Supabase anon key for the LaminDB instance (if env is set to null)
  supabase_anon_key = secrets.SUPABASE_ANON_KEY
  // The number of retries for API requests
  max_retries = 3
  // The delay between retries in milliseconds
  retry_delay = 100
}
```

In addition to the above settings, you can also set the following
environment variables:

```bash
export LAMIN_CURRENT_INSTANCE="laminlabs/lamindata"
export LAMIN_API_KEY="your-lamin-api-key"
export LAMIN_CURRENT_PROJECT="your-lamin-project"
export LAMIN_ENV="prod"
export SUPABASE_API_URL="https://your-supabase-api-url.supabase.co"
export SUPABASE_ANON_KEY="your-supabase-anon-key"
export LAMIN_MAX_RETRIES=3
export LAMIN_RETRY_DELAY=100
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about
contributing to this repository.
