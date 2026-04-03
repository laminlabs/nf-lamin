#!/bin/bash

export NXF_VER=25.10.4
nextflow \
  -trace ai.lamin \
  run nf-core/fetchngs \
  -r 1.12.0 \
  -profile docker,test \
  -c examples/fetchngs/nextflow.config \
  --outdir output/fetchngs/
