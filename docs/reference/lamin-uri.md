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

:::{warning}
The publish URI grammar is **experimental** and may change before it is settled -- see
[nf-lamin#152](https://github.com/laminlabs/nf-lamin/issues/152). All three forms below are
accepted today.
:::

```
lamin://<owner>/<instance>?space=<uid>&storage=<uid>&prefix=<key>
lamin://<owner>/<instance>/space/<uid>?storage=<uid>&prefix=<key>
lamin://<owner>/<instance>/storage/<uid>?prefix=<key>
```

**Components:**

- `space` - (Optional) UID of the space to publish into. The storage location of that space
  is used, which is the same one LaminDB would pick.
- `storage` - (Optional) UID of the storage location to publish into. Defaults to the
  instance's default storage.
- `prefix` - (Optional) Key prefix within the storage location, e.g. `results`

All three forms are normalised to the same target, so `?space=` and `/space/<uid>` mean the
same thing. Whichever form you write, the path of a published file is always rendered in the
canonical form:

```
lamin://laminlabs/lamindata/storage/JwMEKs04D9WJ?prefix=results/qc/report.html
```

The key of a published file always lives in `prefix`, never in the path -- that keeps the path
unambiguous, so a key starting with `artifact/` or `space/` can never be mistaken for a
selector.

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

### What gets registered

Each published file is registered as an Artifact whose `key` is its path relative to the
storage root. Because the file already lives in a registered storage location, LaminDB
records it in place rather than copying it, and the artifact's `key` is the real storage key
(`_key_is_virtual` is `false`).

Publishing itself only needs the plugin; registering the published files as artifacts needs
run tracking to be configured (`lamin.instance` and `lamin.api_key`).

### Restrictions

- The storage location must be **managed by the instance** you are publishing to. Publishing
  to a storage location owned by another instance is refused.
- `.lamindb/` is **reserved** by LaminDB for auto-managed artifacts and cannot be used as a
  publish prefix.
- A space and a storage that belong to different spaces cannot be combined -- LaminDB requires
  them to match.
- Artifact URIs are read-only.

## Credential federation (automatic, S3)

For artifacts stored in LaminHub-managed **S3** storage, the plugin automatically obtains temporary STS session credentials from LaminHub and uses them to stage the file. No AWS credential configuration is required in `nextflow.config`.

The plugin resolves the `lamin://` URI to the artifact's storage location (`storageRoot` and key), then calls LaminHub's cloud-access API to get short-lived `AccessKeyId` / `SecretAccessKey` / `SessionToken` credentials scoped to that storage root. Nextflow stages the file through an internal `lamin-s3://` virtual filesystem backed by those credentials. The credentials are refreshed as needed, so a long-running transfer is not cut short by their expiry.

The same credentials are used for publishing, provided LaminHub grants `write` or `admin` access to the storage location. If it grants only `read`, publishing fails rather than falling back to your own AWS credentials -- that would write under a different identity than the one authorised.

This feature can be turned off by setting `lamin.features.manage_s3_credentials = false` in `nextflow.config`, in which case the plugin will resolve `lamin://` URIs -- for reading and for publishing -- using the default credential provider chain (e.g. environment variables, AWS credentials file, EC2 instance profile, etc).

Storage locations that LaminHub does not manage are always resolved that way, through the
`s3://` (nf-amazon) or `gs://` (nf-google) provider and whatever credentials Nextflow is
configured with.
