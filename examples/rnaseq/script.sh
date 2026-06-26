#!/bin/bash

export TOWER_WORKSPACE_ID="14874785379374"
export NXF_VER=25.10.5
export NXF_SYNTAX_PARSER=v1

# uncomment this for production
CONFIG=configs/env-prod.config
OUTDIR="$LAMIN_TEST_BUCKET/scrnaseq/run_$(date +%Y%m%d_%H%M%S)"

# uncomment this for staging
CONFIG=configs/env-staging.config
OUTDIR="${LAMIN_TEST_BUCKET}-staging/scrnaseq/run_$(date +%Y%m%d_%H%M%S)"

nextflow \
  -trace ai.lamin \
  run nf-core/rnaseq \
  -r 3.23.0 \
  -profile docker,test \
  -c examples/rnaseq/nextflow.config \
  -c $CONFIG \
  --outdir $OUTDIR \
  -with-tower
