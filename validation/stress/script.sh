#!/bin/bash

export TOWER_WORKSPACE_ID="14874785379374"
export NXF_VER=25.10.5
export NXF_SYNTAX_PARSER=v1

# Load shape (override on the command line, e.g. N_FILES=5000 validation/stress/script.sh)
N_FILES=${N_FILES:-1000}            # total artifacts to create
FILES_PER_TASK=${FILES_PER_TASK:-100}  # files per Nextflow task
FILE_SIZE=${FILE_SIZE:-1024}        # bytes per artifact
TIME_SPREAD=${TIME_SPREAD:-0}       # spread task completion over N seconds (0 = burst)
MAX_FORKS=${MAX_FORKS:-16}          # max concurrent generator tasks

# # uncomment this for production
# RUN=run_$(date +%Y%m%d_%H%M%S)
# CONFIG=configs/env-prod.config
# OUTDIR="$LAMIN_TEST_BUCKET/stress/${RUN}"
# REPORTDIR="out/stress-report/prod-${RUN}"

# uncomment this for staging
RUN=run_$(date +%Y%m%d_%H%M%S)
CONFIG=configs/env-staging.config
OUTDIR="${LAMIN_TEST_BUCKET}-staging/stress/${RUN}"
REPORTDIR="out/stress-report/staging-${RUN}"

nextflow \
  -trace ai.lamin \
  run laminlabs/nf-lamin \
  -r main \
  -latest \
  -main-script validation/stress/main.nf \
  -c validation/stress/nextflow.config \
  -c $CONFIG \
  --n_files $N_FILES \
  --files_per_task $FILES_PER_TASK \
  --file_size $FILE_SIZE \
  --time_spread $TIME_SPREAD \
  --max_forks $MAX_FORKS \
  -output-dir $OUTDIR \
  -with-tower

scripts/visualise_api_calls.R .nextflow.log $REPORTDIR
