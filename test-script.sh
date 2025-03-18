#!/bin/bash

# run this the first time:
#   git clone -b v24.10.5 https://github.com/nextflow-io/nextflow ../nextflow
#   echo "includeBuild('../nextflow')" >> settings.gradle
#   make compile

make assemble

# ./launch.sh run nf-core/scrnaseq \
#   -latest -resume \
#   -plugins nf-lamin \
#   -r 2.7.1 \
#   -profile docker,test \
#   --input https://github.com/nf-core/test-datasets/raw/scrnaseq/samplesheet-2-0.csv \
#   --fasta https://github.com/nf-core/test-datasets/raw/scrnaseq/reference/GRCm38.p6.genome.chr19.fa \
#   --gtf https://github.com/nf-core/test-datasets/raw/scrnaseq/reference/gencode.vM19.annotation.chr19.gtf \
#   --protocol 10XV2 \
#   --skip_emptydrops \
#   --outdir scrnaseq_output


./launch.sh run https://packages.viash-hub.com/vsh/toolbox.git \
  -revision v0.1.0 \
  -main-script target/nextflow/bgzip/main.nf \
  -profile docker \
  -latest \
  -resume \
  -plugins nf-lamin \
  --input https://github.com/nf-core/test-datasets/raw/scrnaseq/samplesheet-2-0.csv \
  --output output.gz \
  --publish_dir gs://di-temporary/scratch/temp-nf-lamin
