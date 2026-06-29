#!/usr/bin/env bash
#
# Fetch a Seqera Platform (Tower) run, figure out which Lamin environment and
# publish-dir date it used, dump + extract its logs, and render the API-call
# report with scripts/visualise_api_calls.R.
#
# Usage:
#   out/stress-report/script.sh <run-id> [workspace]
#
# Examples:
#   out/stress-report/script.sh Tm7FIgPuR7V8R
#   out/stress-report/script.sh Tm7FIgPuR7V8R 14874785379374
#
# The environment (staging/prod) and the publish-dir date are read from the
# run's config + params via `tw runs view` *before* downloading the logs, so the
# output directory is named meaningfully from the start. As a fallback they are
# recovered from the s3 path / `env = '...'` line in the dumped log itself.

set -euo pipefail

RUN_ID="${1:-}"
# Workspace the run lives in. Defaults to the lamin_workflows workspace used for
# the stress tests; override with arg 2 or the TOWER_WORKSPACE_ID env var.
WORKSPACE="${2:-${TOWER_WORKSPACE_ID:-14874785379374}}"

if [[ -z "$RUN_ID" ]]; then
  echo "Usage: $0 <run-id> [workspace]" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)/out/stress-report"
R_SCRIPT="$SCRIPT_DIR/visualise_api_calls.R"

tw_view() { tw runs view -w "$WORKSPACE" -i "$RUN_ID" "$@" 2>/dev/null; }

# ---- 1. determine env + publish date from run metadata (before fetching logs)-
echo ">> Inspecting run $RUN_ID in workspace $WORKSPACE ..."

# Lamin env from the `lamin { env = '...' }` block in the resolved config.
ENV="$(tw_view --config | grep -ioE "env[[:space:]]*=[[:space:]]*['\"][a-z]+['\"]" \
        | grep -ioE "['\"][a-z]+['\"]" | tr -d "\"'" | head -n1 || true)"

# Publish-dir date from the params `outdir` (e.g. .../stress/run_2026-06-28_11-25-21).
OUTDIR="$(tw_view --params | grep -ioE "s3://[^ \"']*stress/run_[0-9_-]+" | head -n1 || true)"
RUN_STAMP="$(printf '%s\n' "$OUTDIR" | grep -oE "run_[0-9-]{10}_[0-9-]{8}" | sed 's/^run_//' || true)"

echo "   env         : ${ENV:-<unknown>}"
echo "   outdir      : ${OUTDIR:-<unknown>}"
echo "   publish date: ${RUN_STAMP:-<unknown>}"

# Fall back to a timestamp-free label if metadata lookup came up empty; the log
# fallback below will refine it once the dump is extracted.
ENV_LABEL="${ENV:-env}"
STAMP_LABEL="${RUN_STAMP:-$RUN_ID}"
OUTDIR_PATH="$OUT_ROOT/${ENV_LABEL}-tw-run_${STAMP_LABEL}"

# ---- 2. dump + extract the run ----------------------------------------------
# Raw dump (json metadata + nextflow.log) goes in dump/ so it doesn't collide
# with the report files the R script writes to $OUTDIR_PATH.
TARBALL="$OUTDIR_PATH.tar.xz"
DUMP_DIR="$OUTDIR_PATH/dump"
mkdir -p "$DUMP_DIR"

echo ">> Dumping run logs to $TARBALL ..."
tw runs dump -w "$WORKSPACE" -i "$RUN_ID" -o "$TARBALL" --silent

echo ">> Extracting to $DUMP_DIR ..."
tar -xf "$TARBALL" -C "$DUMP_DIR"
rm -f "$TARBALL"

# ---- 3. fallback: recover env / date from the log if metadata was missing ----
LOG_FILE="$DUMP_DIR/nextflow.log"
if [[ ! -f "$LOG_FILE" ]]; then
  LOG_FILE="$(find "$DUMP_DIR" -maxdepth 1 -name '*.log' | head -n1)"
fi
if [[ -z "$LOG_FILE" || ! -f "$LOG_FILE" ]]; then
  echo "ERROR: no nextflow log found in $OUTDIR_PATH" >&2
  exit 1
fi

if [[ -z "${ENV:-}" ]]; then
  ENV="$(grep -ioE "env[[:space:]]*=[[:space:]]*['\"][a-z]+['\"]" "$LOG_FILE" \
          | grep -ioE "['\"][a-z]+['\"]" | tr -d "\"'" | head -n1 || true)"
  echo "   (recovered env from log: ${ENV:-<unknown>})"
fi
if [[ -z "${RUN_STAMP:-}" ]]; then
  RUN_STAMP="$(grep -oE "stress/run_[0-9-]{10}_[0-9-]{8}" "$LOG_FILE" \
                | head -n1 | sed 's#stress/run_##' || true)"
  echo "   (recovered publish date from log: ${RUN_STAMP:-<unknown>})"
fi

# ---- 4. render the report ----------------------------------------------------
echo ">> Running visualise_api_calls.R on $LOG_FILE ..."
"$R_SCRIPT" "$LOG_FILE" "$OUTDIR_PATH"

echo ">> Done. Report written to $OUTDIR_PATH"
