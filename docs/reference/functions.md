# Functions

## `getRunUid()`

Returns the UID of the current Lamin run.

**Returns:** `String` - The run UID, or `null` if the plugin hasn't initialized the run yet.

**Example:**

```groovy
include { getRunUid } from 'plugin/nf-lamin'

workflow {
  def runUid = getRunUid()
  log.info "Current run: ${runUid}"
}
```

## `getTransformUid()`

Returns the UID of the current Lamin transform.

**Returns:** `String` - The transform UID, or `null` if the plugin hasn't initialized the transform yet.

**Example:**

```groovy
include { getTransformUid } from 'plugin/nf-lamin'

workflow {
  def transformUid = getTransformUid()
  log.info "Current transform: ${transformUid}"
}
```

## `getInstanceSlug()`

Returns the currently configured LaminDB instance identifier.

**Returns:** `String` - The instance slug in the format "owner/name" (e.g., "laminlabs/lamindata"), or `null` if not available.

**Example:**

```groovy
include { getInstanceSlug } from 'plugin/nf-lamin'

workflow {
  def instance = getInstanceSlug()
  log.info "Connected to LaminDB instance: ${instance}"
}
```

## `annotateArtifact()`

Attaches metadata to the artifact that Lamin registers for a file.

This function records what should be attached to the resulting artifact, which lets a workflow decide its metadata programmatically instead of only through static config.

**Parameters:**

| Name           | Type          | Description                                    |
| -------------- | ------------- | ---------------------------------------------- |
| _(positional)_ | Path/String   | The file to annotate, or a collection of files |
| `kind`         | String        | Artifact kind, e.g. `dataset`, `model`, `plan` |
| `description`  | String        | Artifact description                           |
| `ulabel_uids`  | List\<String> | ULabel UIDs or named references to link        |
| `project_uids` | List\<String> | Project UIDs or named references to link       |

Like the [UID fields in the config](config.md#core-settings), `ulabel_uids` and `project_uids` accept a UID, or a named reference: `'?name'` (look up, omit if missing), `'!name'` (look up, error if missing), `'+name'` (create if missing).

**Returns:** the file it was given, unchanged, so the call can be the body of a `map` closure.

**Example:**

```groovy
include { annotateArtifact } from 'plugin/nf-lamin'

workflow {
  ALIGN(ch_reads)
    | map { meta, bam ->
        annotateArtifact(bam,
          kind: 'dataset',
          description: "Aligned reads for sample ${meta.id}",
          ulabel_uids: ['+alignment', meta.qc_passed ? '+qc-passed' : '+qc-failed']
        )
        [meta, bam]
      }
    | set { ch_bam }

  publish:
  bam = ch_bam
}
```

**Notes:**

- It can be called before or after the file is published. The annotation is applied whenever the artifact is registered.
- Repeated calls for the same file accumulate.
- It does nothing when no Lamin instance is configured, so a workflow using it still runs without the plugin being connected.
- A file that is annotated but never tracked as an artifact is reported in a warning at the end of the run.

---

All functions return `null` if the plugin hasn't initialized the run yet, so they are best used in the workflow body (not in process definitions).
