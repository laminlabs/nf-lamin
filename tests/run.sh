#!/bin/bash

set -e

if [ -z "$LAMIN_TEST_BUCKET" ]; then
  echo "Please set the LAMIN_TEST_BUCKET environment variable to a valid bucket which you have write access to."
  exit 1
fi

make && make install

nextflow \
  -trace ai.lamin \
  run nf-core/scrnaseq \
  -latest \
  -r "2.7.1" \
  -profile test,docker \
  --outdir ${LAMIN_TEST_BUCKET}/temp-scrnaseq/run_$(date +%Y%m%d_%H%M%S) \
  -resume \
  -with-report report.html \
  -with-trace trace.txt \
  -with-timeline timeline.html \
  -with-dag flowchart.png
