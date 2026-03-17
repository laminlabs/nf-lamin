#!/bin/bash

export NXF_VER=25.10.4
nextflow \
  -trace ai.lamin \
  run bigbio/quantms \
  -r 1.7.0 \
  -profile docker,test_lfq \
  -c examples/quantms/nextflow.config \
  --outdir output/quantms/
