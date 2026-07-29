# validation/publish_run

Publishes Nextflow outputs into a Lamin storage location with **Nextflow 26.04+**, using a
`lamin://` output directory.

Exercises:

- `-output-dir 'lamin://<owner>/<instance>'` as a publish target
- the `output { }` block with a per-record `path`
- an `index` file, which goes through the byte-channel write path rather than the upload path
- artifact registration of each published file, with the key LaminDB derives from the
  storage-relative path

## Run

```bash
make validate-publish-run INSTANCE=laminlabs/lamin-dev
# publish under a prefix:
make validate-publish-run INSTANCE=laminlabs/lamin-dev PREFIX=nf-lamin-test
```

## Grammars

The publish URI grammar is not settled yet (see
[#152](https://github.com/laminlabs/nf-lamin/issues/152)). `make validate-publish-grammars`
runs this workflow once per accepted form against the same instance, so the artifacts they
produce can be compared:

```
lamin://<owner>/<instance>?prefix=...
lamin://<owner>/<instance>/space/<space-uid>?prefix=...
lamin://<owner>/<instance>/storage/<storage-uid>?prefix=...
```

Pass `SPACE=<uid>` and `STORAGE=<uid>` to exercise the second and third forms.
