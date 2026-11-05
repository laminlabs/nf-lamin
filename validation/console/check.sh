#!/usr/bin/env bash
#
# Check what the plugin prints on the console. Runs without -trace on purpose, since that flag
# makes plugin logs show regardless of the logger's name.
#
# Usage: check.sh <plugin version>
#
# With LAMIN_API_KEY and LAMIN_CURRENT_INSTANCE set, also checks a run with a valid key.

set -uo pipefail

version=$1
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
failures=0

# agent mode changes the output
unset CLAUDECODE AGENT NXF_AGENT_MODE
export NXF_ANSI_LOG=false

# keep .nextflow.log of the other validation workflows
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
cd "$work_dir" || exit 1

run() {
  local config=$1
  cat > lamin.config <<EOF
lamin {
  $config
  env = '${LAMIN_ENV:-prod}'
}
EOF
  output=$(nextflow run "$script_dir/main.nf" -plugins "nf-lamin@$version" -c lamin.config 2>&1)
  exit_code=$?
}

expect() {
  local description=$1
  shift
  if "$@"; then
    echo "ok: $description"
  else
    echo "FAILED: $description"
    failures=$((failures + 1))
  fi
}

shows() { grep -qF -- "$1" <<< "$output"; }
hides() { ! shows "$1"; }
count_is() { [ "$(grep -cF -- "$1" <<< "$output")" -eq "$2" ]; }

echo "--- invalid API key"
run "instance = 'laminlabs/lamin-dev'; api_key = 'not-a-real-key'"
expect "the pipeline succeeds" [ "$exit_code" -eq 0 ]
expect "the failure is shown as one warning" count_is "Could not initialize Lamin run" 1
expect "the reason is shown" shows "API key not found"
expect "no error is shown" hides "ERROR ~"
expect "the API key is masked" hides "not-a-real-key"

if [ -n "${LAMIN_API_KEY:-}" ] && [ -n "${LAMIN_CURRENT_INSTANCE:-}" ]; then
  echo "--- valid API key"
  run "instance = '$LAMIN_CURRENT_INSTANCE'; api_key = System.getenv('LAMIN_API_KEY')"
  expect "the pipeline succeeds" [ "$exit_code" -eq 0 ]
  expect "the connection is shown" shows "connected lamindb"
  expect "the link to the run is shown" shows "/transform/"
  expect "no warning is shown" hides "WARN"
fi

if [ "$failures" -gt 0 ]; then
  echo "Console output:"
  echo "$output"
  exit 1
fi
