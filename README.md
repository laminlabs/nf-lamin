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

    [33mNextflow 25.04.4 is available - Please consider updating your version to it(B[m
    N E X T F L O W  ~  version 24.10.5
    Pulling vsh/toolbox ...
    Launching `https://packages.viash-hub.com/vsh/toolbox` [hungry_monod] DSL2 - revision: 5cb3251eaf [v0.1.1]
    ✅ Connected to LaminDB instance 'laminlabs/lamindata' as 'rcannood'
    Transform W818bFm1ecyM0002 (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002)
    Run XYQm4OOQc84Bc06inTMB (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002/XYQm4OOQc84Bc06inTMB)
    [40/5d0a19] Submitted process > bgzip:processWf:bgzip_process (run)
    [ae/e3d436] Submitted process > bgzip:publishFilesSimpleWf:publishFilesProc (run)
    [d4/e85685] Submitted process > bgzip:publishStatesSimpleWf:publishStatesProc (run)

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
    DEBUG nextflow.lamin.instance.Instance - PUT createRecord: core.run, data=[transform_id:330.0, name:hungry_monod, created_at:2025-06-17T15:17:53.119112153+02:00, started_at:2025-06-17T15:17:53.119112153+02:00, _status_code:-1]
    DEBUG nextflow.lamin.instance.Instance - Response from createRecord: [[id:587.0, uid:XYQm4OOQc84Bc06inTMB, _aux:null, name:hungry_monod, space_id:1.0, _branch_code:1.0, report_id:null, _logfile_id:null, reference:null, transform_id:330.0, created_at:2025-06-17T13:17:55.882512+00:00, created_by_id:28.0, started_at:2025-06-17T13:17:53.119112+00:00, environment_id:null, finished_at:null, _status_code:-1.0, reference_type:null, _is_consecutive:null, initiated_by_run_id:null]]
    INFO  nextflow.lamin.LaminObserver - Run XYQm4OOQc84Bc06inTMB (https://staging.laminhub.com/laminlabs/lamindata/transform/W818bFm1ecyM0002/XYQm4OOQc84Bc06inTMB)
    Jun-17 15:18:00.117 [TaskFinalizer-1] DEBUG nextflow.lamin.LaminObserver - onProcessComplete triggered!
    Jun-17 15:18:01.023 [TaskFinalizer-3] DEBUG nextflow.lamin.LaminObserver - onProcessComplete triggered!
    Jun-17 15:18:01.023 [TaskFinalizer-2] DEBUG nextflow.lamin.LaminObserver - onProcessComplete triggered!
    Jun-17 15:18:01.454 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - onFilePublish triggered!
    Jun-17 15:18:01.456 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 587 at file:///home/rcannood/workspace/laminlabs/nf-lamin/work/d4/e85685d7aea0aabac5846412e8d045/run.bgzip.state.yaml
    Jun-17 15:18:01.457 [PublishDir-2] DEBUG nextflow.lamin.instance.Instance - POST /instances/{instance_id}/artifacts/create: {"path":"gs://di-temporary-public/scratch/temp-nf-lamin/run_20250617_151750/run.bgzip.state.yaml","kwargs":{"run_id":587,"description":"Output artifact for run 587"}}
    Jun-17 15:18:01.465 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - onFilePublish triggered!
    Jun-17 15:18:01.465 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Creating output artifact for run 587 at file:///home/rcannood/workspace/laminlabs/nf-lamin/work/ae/e3d43647123f0c8eee764f87cc5cfe/output.gz
    Jun-17 15:18:01.466 [PublishDir-1] DEBUG nextflow.lamin.instance.Instance - POST /instances/{instance_id}/artifacts/create: {"path":"gs://di-temporary-public/scratch/temp-nf-lamin/run_20250617_151750/output.gz","kwargs":{"run_id":587,"description":"Output artifact for run 587"}}
    Jun-17 15:18:04.085 [PublishDir-1] DEBUG nextflow.lamin.instance.Instance - Response from createArtifact: [statusCode:200.0, body:[message:Artifact created successfully, artifact:[version:null, is_latest:true, branch:1.0, space:1.0, _aux:null, created_at:2025-06-17T13:18:03.976Z, updated_at:2025-06-17T13:18:03.976Z, uid:j2rDifVGLVPP18bL0000, key:scratch/temp-nf-lamin/run_20250617_151750/output.gz, description:Output artifact for run 587, storage:32.0, suffix:.gz, kind:null, otype:null, size:86.0, hash:gQNGvAfn0QSKuEe/j3Swew, n_files:null, n_observations:null, _hash_type:md5, run:587.0, schema:null, _key_is_virtual:false, created_by:28.0, _overwrite_versions:false, input_of_runs:[], _subsequent_runs:[], _actions:[]]]]
    Jun-17 15:18:04.086 [PublishDir-1] DEBUG nextflow.lamin.LaminObserver - Created output artifact j2rDifVGLVPP18bL0000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/j2rDifVGLVPP18bL0000)
    Jun-17 15:18:04.484 [PublishDir-2] DEBUG nextflow.lamin.instance.Instance - Response from createArtifact: [statusCode:200.0, body:[message:Artifact created successfully, artifact:[version:null, is_latest:true, branch:1.0, space:1.0, _aux:null, created_at:2025-06-16T15:22:04.384Z, updated_at:2025-06-16T15:22:04.384Z, uid:jAxjm3Kj39aiPUW30000, key:scratch/temp-nf-lamin/run.bgzip.state.yaml, description:Output artifact for run 587, storage:32.0, suffix:.yaml, kind:null, otype:null, size:35.0, hash:p41ifSZvoMr4+4FoOJBpwA, n_files:null, n_observations:null, _hash_type:md5, run:563.0, schema:null, _key_is_virtual:false, created_by:28.0, _overwrite_versions:false, input_of_runs:[], _subsequent_runs:[], _actions:[]]]]
    Jun-17 15:18:04.485 [PublishDir-2] DEBUG nextflow.lamin.LaminObserver - Detected previous output artifact jAxjm3Kj39aiPUW30000 (https://staging.laminhub.com/laminlabs/lamindata/artifact/jAxjm3Kj39aiPUW30000)
    DEBUG nextflow.lamin.LaminObserver - onFlowComplete triggered!
    DEBUG nextflow.lamin.instance.Instance - PATCH updateRecord: core.run, uid=XYQm4OOQc84Bc06inTMB, data=[finished_at:2025-06-17T15:18:04.485764124+02:00, _status_code:0]
    DEBUG nextflow.lamin.instance.Instance - Response from updateRecord: [id:587.0, uid:XYQm4OOQc84Bc06inTMB, _aux:null, name:hungry_monod, space_id:1.0, _branch_code:1.0, report_id:null, _logfile_id:null, reference:null, transform_id:330.0, created_at:2025-06-17T13:17:55.882512+00:00, created_by_id:28.0, started_at:2025-06-17T13:17:53.119112+00:00, environment_id:null, finished_at:2025-06-17T13:18:04.485764+00:00, _status_code:0.0, reference_type:null, _is_consecutive:null, initiated_by_run_id:null]

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
}
```

In addition to the above settings, you can also set the following
environment variables:

```bash
export LAMIN_INSTANCE="laminlabs/lamindata"
export LAMIN_API_KEY="your-lamin-api-key"
export LAMIN_PROJECT="your-lamin-project"
export LAMIN_ENV="prod"
export LAMIN_SUPABASE_API_URL="https://your-supabase-api-url.supabase.co"
export LAMIN_SUPABASE_ANON_KEY="your-supabase-anon-key"
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about
contributing to this repository.
