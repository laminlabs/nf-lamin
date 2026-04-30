# Lamin URIs

The `nf-lamin` plugin provides native support for `lamin://` URIs, allowing you to reference LaminDB artifacts directly in your Nextflow workflows using Nextflow's standard `file()` function.

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

## Basic usage

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

## With sub-paths

For artifacts that are directories or archives, reference specific files within them:

```groovy
workflow {
  // Reference a specific file within an artifact directory
  def config_file = file('lamin://myorg/myinstance/artifact/abcd1234efgh5678/config/settings.yaml')

  Channel.of(config_file)
    | processConfig
}
```

## As workflow parameters

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

## Requirements

- The `nf-lamin` plugin must be configured with a valid API key
- The workflow must have started (the plugin initializes on workflow start)

## Credential federation (automatic, S3)

For artifacts stored in LaminHub-managed **S3** storage, the plugin automatically obtains temporary STS session credentials from LaminHub and uses them to stage the file. No AWS credential configuration is required in `nextflow.config`.

The plugin resolves the `lamin://` URI to the artifact's storage location (`storageRoot` and key), then calls LaminHub's cloud-access API to get short-lived `AccessKeyId` / `SecretAccessKey` / `SessionToken` credentials scoped to that storage root. Nextflow stages the file through an internal `lamin-s3://` virtual filesystem backed by those credentials.

This feature can be turned off by setting `lamin.features.manage_s3_credentials = false` in `nextflow.config`, in which case the plugin will attempt to resolve `lamin://` URIs using the default credential provider chain (e.g. environment variables, AWS credentials file, EC2 instance profile, etc).

## Limitations

- `lamin://` paths are **read-only** — you cannot write to them
