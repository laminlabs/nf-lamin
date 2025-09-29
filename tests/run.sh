#!/bin/bash

make && make install

nextflow \
  -trace ai.lamin \
  run nf-core/scrnaseq \
  -latest \
  -r "2.7.1" \
  -profile test,docker \
  --outdir gs://di-temporary-public/scratch/temp-scrnaseq/run_$(date +%Y%m%d_%H%M%S) \
  -resume \
  -with-report report.html \
  -with-trace trace.txt \
  -with-timeline timeline.html \
  -with-dag flowchart.png
