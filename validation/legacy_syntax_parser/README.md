# validation/legacy_syntax_parser

Tests nf-lamin with **Nextflow < 26.04** (legacy DSL2 syntax parser).

Uses the classic `publishDir` directive (closure form) and tuple-based process I/O.
Compatible with `NXF_SYNTAX_PARSER=v1` and all Nextflow versions back to 25.04.

## Run

```bash
make validate-legacy
# or with custom artifact:
make validate-legacy ARGS="--artifact-uri lamin://org/instance/artifact/uid16chars"
```
