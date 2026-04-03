#!/bin/bash

export NXF_VER=25.10.4
nextflow run nf-core/scrnaseq \
  -r 4.1.0 \
  -profile docker,test \
  -c examples/scrnaseq/nextflow.config \
  --outdir output/scrnaseq/
