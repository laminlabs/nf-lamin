# validation/publish_legacy

Publishes to a `lamin://` target through `publishDir`, with the legacy DSL2 syntax parser
(Nextflow < 26.04).

Exercises:

- `publishDir 'lamin://<owner>/<instance>?prefix=...'`
- publishing a **directory**, which takes a different route through `PublishDir` than a
  single file
- optionally a file above the multipart threshold, via `--largeFileMiB`
- re-running with `-resume`, which sends `PublishDir` down the
  `FileAlreadyExists → hash → delete → reupload` branch

## Run

```bash
make validate-publish-legacy INSTANCE=laminlabs/lamin-dev
# with a large file and a rerun to exercise the overwrite path:
make validate-publish-legacy INSTANCE=laminlabs/lamin-dev ARGS="--largeFileMiB 150"
make validate-publish-legacy INSTANCE=laminlabs/lamin-dev ARGS="-resume"
```
