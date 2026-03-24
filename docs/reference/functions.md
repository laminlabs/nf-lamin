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

All functions return `null` if the plugin hasn't initialized the run yet, so they are best used in the workflow body (not in process definitions).
