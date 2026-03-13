#!/bin/bash

export NXF_VER=25.10.4
nextflow run nf-core/rnaseq \
  -r 3.23.0 \
  -profile docker,test \
  -c examples/rnaseq/nextflow.config \
  --outdir output/rnaseq/
