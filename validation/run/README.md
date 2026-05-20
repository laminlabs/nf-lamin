# validation/run

Tests nf-lamin with **Nextflow 26.04+** (strict syntax parser, enabled by default).

Exercises the following 26.04 features together with the nf-lamin plugin:

- **Typed params** (`params { }` block with type annotations)
- **Record types** (`record InputSample { ... }`, `record SummarizeResult { ... }`)
- **Typed process** (destructured record input, record output)
- **Typed workflow** (`take:` / `emit:` with `Channel<T>` annotations)
- **Workflow output block** (`output { }` + `publish:` section, `-output-dir` flag)

## Run

```bash
make validate-run
# or with custom artifact:
make validate-run ARGS="--artifact-uri lamin://org/instance/artifact/uid16chars"
```
