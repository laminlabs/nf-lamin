# Lamin URIs

The `nf-lamin` plugin provides native support for `lamin://` URIs, allowing you to reference LaminDB artifacts directly in your Nextflow workflows using Nextflow's standard `file()` function, and to publish workflow outputs into a LaminDB instance's storage.

## Reading an artifact

```
lamin://<owner>/<instance>/artifact/<uid>[/<subpath>]
```

**Components:**

- `owner` - The LaminDB instance owner (organization or user)
- `instance` - The LaminDB instance name
- `uid` - The artifact UID (16 or 20 characters)
  - 16-character base UIDs fetch the most recently updated version
  - 20-character full UIDs fetch that specific version
- `subpath` - (Optional) Path within the artifact for directories or archives

### Basic usage

Use `lamin://` URIs with the `file()` function:

```groovy
workflow {
  // Reference a LaminDB artifact directly by URI
  def input_file = file('lamin://laminlabs/lamindata/artifact/PnNjE93TdZGJ')

  log.info "Using artifact: ${input_file}"

  Channel.of(input_file)
    | myProcess
}
```

### With sub-paths

For artifacts that are directories or archives, reference specific files within them:

```groovy
workflow {
  // Reference a specific file within an artifact directory
  def config_file = file('lamin://myorg/myinstance/artifact/abcd1234efgh5678/config/settings.yaml')

  Channel.of(config_file)
    | processConfig
}
```

### As workflow parameters

Use `lamin://` URIs as workflow parameters:

```groovy
params.input = 'lamin://laminlabs/lamindata/artifact/PnNjE93TdZGJ'

workflow {
  Channel.fromPath(params.input)
    | myProcess
}
```

Or pass them on the command line:

```bash
nextflow run my-pipeline.nf --input 'lamin://laminlabs/lamindata/artifact/PnNjE93TdZGJ'
```

## Publishing to a storage location

```
lamin://<owner>/<instance>?space=<uid>&storage=<uid>&prefix=<key>
```

**Components:**

- `space` - (Optional) UID of the space to publish into. Its storage location is used, the same one LaminDB would pick.
- `storage` - (Optional) UID of the storage location to publish into. Defaults to the instance's default storage location.
- `prefix` - (Optional) Key prefix within the storage location, e.g. `results`

All three are optional, so `lamin://laminlabs/lamindata` publishes into the root of the instance's default storage location. Space and storage are selected by **uid**, not by name.

### Publishing all workflow outputs

```bash
nextflow run my-pipeline.nf -output-dir 'lamin://laminlabs/lamindata?prefix=results'
```

### Publishing from a process

```groovy
process myProcess {
  publishDir 'lamin://laminlabs/lamindata?prefix=results', mode: 'copy'
  ...
}
```

### What happens

When Nextflow parses the target, the plugin looks up the space and storage in the instance and resolves the target to a path in that storage location. Everything Nextflow publishes there is written through the federated credentials LaminHub grants for the location, and each published file is registered as an Artifact **in place**: its `key` is the path relative to the storage root, LaminDB does not copy it, and it is addressed as `lamin://<owner>/<instance>/artifact/<uid>` from then on.

Registering the published files as artifacts needs run tracking to be configured, which takes two things.

First, a LaminHub API key, which is also what authorises the writes: set `lamin.api_key` in `nextflow.config`, or the `LAMIN_API_KEY` environment variable.

Second, the LaminDB instance to track the run in. A `lamin://` output directory names it already, so for pipelines that use workflow outputs, `-output-dir 'lamin://<owner>/<instance>?prefix=results'` is all it takes. Pipelines that publish with `publishDir` need `lamin.instance` in `nextflow.config` or the `LAMIN_CURRENT_INSTANCE` environment variable instead, since their targets are only known once a task publishes. When several of these are set and disagree, the output directory wins over `lamin.instance`, which wins over the environment variable, and the plugin logs a warning (see {doc}`/reference/config`).

Publishing over existing files follows Nextflow's `overwrite` rules, as it does for `s3://` targets.

The storage location a target resolves to decides the space of the artifacts published to it, whether the target names the space, names only the storage, or uses the instance's default storage. `lamin.space_uid` applies only when that location is in the default `all` space.

### Restrictions

- Publishing needs `write` or `admin` access to the storage location on LaminHub. With only `read` access the target is refused; the plugin never falls back to your own AWS credentials for a Lamin-managed location, since that would write under a different identity than the one authorised.
- The storage location must be **managed by the instance** you are publishing to. A location managed by another instance is read-only here.
- When the run is tracked, you can only publish to the instance the run is tracked in, since that is where the published files are registered.
- A space and a storage location that belong to different spaces cannot be combined; LaminDB requires them to match.
- `.lamindb/` is reserved by LaminDB for the artifacts it manages itself and cannot be used as a prefix.
- Artifact URIs are read-only.

## Requirements

- The `nf-lamin` plugin must be loaded (`plugins { id 'nf-lamin' }` or `-plugins nf-lamin`); Nextflow does not load it on its own when it meets a `lamin://` URI
- Reading a public instance works anonymously; reading a private one and publishing need a valid API key

## Credential federation (automatic, S3)

For LaminHub-managed **S3** storage, the plugin automatically obtains temporary STS session credentials from LaminHub and uses them to read and write the files. No AWS credential configuration is required in `nextflow.config`.

The plugin resolves the `lamin://` URI to its storage location (`storageRoot` and key), then calls LaminHub's cloud-access API to get short-lived `AccessKeyId` / `SecretAccessKey` / `SessionToken` credentials scoped to that storage root. Nextflow accesses the files through an internal `lamin-s3://` virtual filesystem backed by those credentials. The credentials are refreshed as needed, so a long-running transfer is not cut short by their expiry.

:::{note}
**Advanced: addressing a storage path directly**

Inputs should normally be referenced as `lamin://` artifact URIs.

For example, a pipeline published its STARsolo output folder `Sample_X.Solo.out` as a folder artifact. The filtered count matrix sits in the subdirectory `Gene/filtered` of that folder. With the artifact uid at hand, you reference it like any other sub-path:

```bash
nextflow run my-pipeline.nf --input 'lamin://laminlabs/lamindata/artifact/3xJthewGRbTTA5PU/Gene/filtered'
```

Behind the scenes, nf-lamin resolves this URI to the path of the folder in S3 and reads it through Lamin-managed federated credentials. You can use such a path directly as well: take a storage path, e.g. one listed in a samplesheet or a log written by the pipeline, and swap `s3://` for `lamin-s3://`. For the folder above, that is:

```bash
nextflow run my-pipeline.nf \
  --input 'lamin-s3://seqera-test-eu/profile-submit/1S6fQH0sbVjs9hMDF7vs/star/Sample_X/Sample_X.Solo.out/Gene/filtered'
```

:::

The same credentials are used to compute the size and hash of artifacts published to a `lamin-s3://` location (see [artifact hashing](config.md#artifact-hashing)).

This feature can be turned off by setting `lamin.features.manage_s3_credentials = false` in `nextflow.config`, in which case the plugin resolves `lamin://` URIs -- for reading and for publishing -- to plain `s3://` paths and lets Nextflow authenticate through the default credential provider chain (environment variables, AWS credentials file, EC2 instance profile, etc).

Storage locations that LaminHub does not manage are always resolved that way, through the `s3://` (nf-amazon) or `gs://` (nf-google) provider and whatever credentials Nextflow is configured with.
