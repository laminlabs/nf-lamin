# Examples

Ready-to-run examples for popular Nextflow pipelines with `nf-lamin` artifact tracking. Source files are in the [`examples/`](https://github.com/laminlabs/nf-lamin/tree/main/examples) directory. All examples follow the [best-practice config](config.md#best-practice-config) pattern.

---

## nf-core/rnaseq

[nf-core/rnaseq](https://nf-co.re/rnaseq/latest) is a bioinformatics pipeline for bulk RNA-seq analysis. It produces gene count matrices, alignment files, QC reports, and more.

### Running

```bash
export NXF_VER=25.10.4
nextflow run nf-core/rnaseq \
  -r 3.23.0 \
  -profile docker,test \
  -c examples/rnaseq/nextflow.config \
  --outdir output/rnaseq/
```

The `test` profile runs on publicly available test data (chr22 subset).

### Configuration

The [`examples/rnaseq/nextflow.config`](https://github.com/laminlabs/nf-lamin/blob/main/examples/rnaseq/nextflow.config) tracks the following:

**Input artifacts:** samplesheet (via `include_paths`), FASTQ reads, reference FASTA, GTF/GFF annotation files.

**Output artifacts (enabled):** MultiQC report, merged gene count matrices (counts, TPM, lengths, scaled), merged transcript quantification files, SummarizedExperiment RDS files, transcript-to-gene mapping.

**Output artifacts (disabled, opt-in):** per-sample Salmon quant files, BAM files, BAM indexes, BigWig coverage tracks, DESeq2 QC outputs, featureCounts tables, FastQC reports/zips, MultiQC raw data, trimming reports, pipeline info.

```groovy
plugins {
  id 'nf-lamin@0.6.2'
}

lamin {
  instance = "laminlabs/lamindata"
  api_key = secrets.LAMIN_API_KEY

  input_artifacts {
    rules {
      samplesheet { include_paths = { params.input }; kind = 'dataset'; order = 1 }
      fastq_reads { pattern = '.*\\.fastq(\\.gz)?$'; kind = 'dataset'; order = 2 }
      reference_fasta { pattern = '.*\\.(fasta|fa)(\\.gz)?$'; kind = 'dataset'; order = 3 }
      annotation { pattern = '.*\\.(gtf|gff|gff3)(\\.gz)?$'; kind = 'dataset'; order = 4 }
    }
  }

  output_artifacts {
    key {
      relativize = { params.outdir }
    }
    exclude_pattern = '.*'
    rules {
      // Enabled by default
      multiqc_report { type = 'include'; pattern = '.*multiqc_report\\.html$'; kind = 'report' }
      gene_counts { type = 'include'; pattern = '.*salmon\\.merged\\.gene_(counts|tpm|counts_length_scaled|counts_scaled|lengths)\\.tsv$'; kind = 'dataset' }
      summarized_experiment { type = 'include'; pattern = '.*\\.SummarizedExperiment\\.rds$'; kind = 'dataset' }
      // Disabled (opt-in)
      bam_files { type = 'include'; enabled = false; pattern = '.*\\.bam$'; kind = 'dataset' }
      bigwig_files { type = 'include'; enabled = false; pattern = '.*\\.bigWig$'; kind = 'dataset' }
      // ... see full config for all rules
    }
  }
}
```

→ [Full config](https://github.com/laminlabs/nf-lamin/blob/main/examples/rnaseq/nextflow.config)

---

## nf-core/scrnaseq

[nf-core/scrnaseq](https://nf-co.re/scrnaseq/latest) is a bioinformatics pipeline for single-cell RNA-seq analysis. It supports multiple aligners (STARSolo, Cellranger, Kallisto, Alevin) and produces count matrices in multiple formats.

### Running

```bash
export NXF_VER=25.10.4
nextflow run nf-core/scrnaseq \
  -r 4.1.0 \
  -profile docker,test \
  -c examples/scrnaseq/nextflow.config \
  --outdir output/scrnaseq/
```

The `test` profile runs on a small chr19 mouse dataset with STARSolo.

### Configuration

The [`examples/scrnaseq/nextflow.config`](https://github.com/laminlabs/nf-lamin/blob/main/examples/scrnaseq/nextflow.config) tracks the following:

**Input artifacts:** samplesheet (via `include_paths`), FASTQ reads, reference FASTA, GTF annotation files.

**Output artifacts (enabled):** MultiQC report, combined filtered/raw h5ad count matrices, per-sample filtered/raw h5ad count matrices.

**Output artifacts (disabled, opt-in):** RDS conversions (Seurat/SingleCellExperiment), BAM files, STARSolo filtered/raw mtx matrices, STARSolo summary statistics, FastQC reports/zips, MultiQC raw data, pipeline info.

```groovy
plugins {
  id 'nf-lamin@0.6.2'
}

lamin {
  instance = "laminlabs/lamindata"
  api_key = secrets.LAMIN_API_KEY

  input_artifacts {
    rules {
      samplesheet { include_paths = { params.input }; kind = 'dataset'; order = 1 }
      fastq_reads { pattern = '.*\\.fastq(\\.gz)?$'; kind = 'dataset'; order = 2 }
      reference_fasta { pattern = '.*\\.(fasta|fa)(\\.gz)?$'; kind = 'dataset'; order = 3 }
      annotation { pattern = '.*\\.(gtf|gff|gff3)(\\.gz)?$'; kind = 'dataset'; order = 4 }
    }
  }

  output_artifacts {
    key {
      relativize = { params.outdir }
    }
    exclude_pattern = '.*'
    rules {
      // Enabled by default
      multiqc_report { type = 'include'; pattern = '.*multiqc_report\\.html$'; kind = 'report' }
      combined_filtered_h5ad { type = 'include'; pattern = '.*/mtx_conversions/combined_filtered_matrix\\.h5ad$'; kind = 'dataset' }
      combined_raw_h5ad { type = 'include'; pattern = '.*/mtx_conversions/combined_raw_matrix\\.h5ad$'; kind = 'dataset' }
      sample_filtered_h5ad { type = 'include'; pattern = '.*/mtx_conversions/.+/.+_filtered_matrix\\.h5ad$'; kind = 'dataset' }
      // Disabled (opt-in)
      rds_conversions { type = 'include'; enabled = false; pattern = '.*/mtx_conversions/.*\\.rds$'; kind = 'dataset' }
      bam_files { type = 'include'; enabled = false; pattern = '.*\\.bam$'; kind = 'dataset' }
      // ... see full config for all rules
    }
  }
}
```

→ [Full config](https://github.com/laminlabs/nf-lamin/blob/main/examples/scrnaseq/nextflow.config)

---

## bigbio/quantms

[bigbio/quantms](https://github.com/bigbio/quantms) is a proteomics pipeline for data-dependent acquisition (DDA) quantitative mass spectrometry. It produces mzTab results, MSstats tables, and QC reports.

### Running

```bash
export NXF_VER=25.10.4
nextflow \
  -trace ai.lamin \
  run bigbio/quantms \
  -r 1.7.0 \
  -profile docker,test_lfq \
  -c examples/quantms/nextflow.config \
  --outdir output/quantms/
```

The `test_lfq` profile runs a label-free quantification workflow on publicly available test data.

### Configuration

The [`examples/quantms/nextflow.config`](https://github.com/laminlabs/nf-lamin/blob/main/examples/quantms/nextflow.config) tracks the following:

**Input artifacts:** SDRF experimental design files, protein database FASTA files.

**Output artifacts (enabled):** mzTab identification/quantification results, MSstats input table, MSstats comparison results, Triqler input table, pMultiQC report.

**Output artifacts (disabled, opt-in):** ConsensusXML intermediate files, spectra statistics (parquet), pMultiQC raw data and plots, pipeline info.

```groovy
plugins {
  id 'nf-lamin@0.6.2'
}

lamin {
  instance = "laminlabs/lamindata"
  api_key = secrets.LAMIN_API_KEY

  input_artifacts {
    rules {
      sdrf { pattern = '.*\.sdrf\.tsv$'; kind = 'dataset' }
      fasta { pattern = '.*\.(fasta|fa)(\.gz)?$'; kind = 'dataset' }
    }
  }

  output_artifacts {
    key {
      relativize = { params.outdir }
    }
    exclude_pattern = '.*'
    rules {
      // Enabled by default
      mztab { type = 'include'; pattern = '.*\.mzTab$'; kind = 'dataset' }
      msstats_in { type = 'include'; pattern = '.*msstats_in\.csv$'; kind = 'dataset' }
      multiqc_report { type = 'include'; pattern = '.*/pmultiqc/multiqc_report\.html$'; kind = 'report' }
      // Disabled (opt-in)
      consensusxml { type = 'include'; enabled = false; pattern = '.*\\.consensusXML$'; kind = 'dataset' }
      spectra_statistics { type = 'include'; enabled = false; pattern = '.*/mzml_statistics/.*\\.parquet$'; kind = 'dataset' }
      // ... see full config for all rules
    }
  }
}
```

→ [Full config](https://github.com/laminlabs/nf-lamin/blob/main/examples/quantms/nextflow.config)
