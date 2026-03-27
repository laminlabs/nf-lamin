# Examples

Ready-to-run examples for popular Nextflow pipelines with `nf-lamin` artifact tracking. Source files are in the [`examples/`](https://github.com/laminlabs/nf-lamin/tree/main/examples) directory.

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

**Output artifacts:** gene count matrices, MultiQC report, BAM alignment files, Salmon quantification files, FastQC reports, BigWig coverage tracks.

**Excluded:** `versions.yml`, pipeline info directory, FastQC zip files, trimming logs, STAR log files.

Artifact keys are derived by stripping the output directory prefix, preserving subdirectory structure (e.g., `star/BSA1_F1.Aligned.sortedByCoord.out.bam`):

```groovy
plugins {
  id 'nf-lamin@0.6.0'
}

lamin {
  instance = "laminlabs/lamindata"
  api_key = secrets.LAMIN_API_KEY

  input_artifacts {
    enabled = true
    rules {
      // Track samplesheet -- uses include_paths because nf-schema's
      // samplesheetToList parses it in Groovy, so it is never staged
      // into a Nextflow process.
      samplesheet { include_paths = { params.input }; kind = 'dataset'; order = 1 }
      fastq_reads { pattern = '.*\\.fastq(\\.gz)?$'; kind = 'dataset'; order = 2 }
      reference_fasta { pattern = '.*\\.(fasta|fa)(\\.gz)?$'; kind = 'dataset'; order = 3 }
      annotation { pattern = '.*\\.(gtf|gff|gff3)(\\.gz)?$'; kind = 'dataset'; order = 4 }
    }
  }

  output_artifacts {
    enabled = true
    key = [relativize: params.outdir]
    exclude_pattern = '.*\\.(log|command\\..*)$'
    rules {
      multiqc_report { pattern = '.*multiqc_report\\.html$'; kind = 'report' }
      gene_counts { pattern = '.*\\.gene_counts.*\\.tsv$'; kind = 'dataset' }
      bam_files { pattern = '.*\\.bam$'; kind = 'dataset' }
      // ... see full config for all rules
    }
  }
}
```

→ [Full config](https://github.com/laminlabs/nf-lamin/blob/main/examples/rnaseq/nextflow.config)

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

**Output artifacts:** mzTab identification/quantification results, MSstats input table, MSstats-processed results, Triqler output, pMultiQC report.

**Excluded:** intermediate OpenMS formats (`.consensusXML`, `.idXML`), pipeline metadata, log files, pMultiQC raw data and plots, `versions.yml`, SDRF copies in the output directory.

Artifact keys are derived using the `[relativize: params.outdir]` Map shorthand:

```groovy
plugins {
  id 'nf-lamin@0.6.0'
}

lamin {
  instance = "laminlabs/lamindata"
  api_key = secrets.LAMIN_API_KEY

  input_artifacts {
    enabled = true
    rules {
      sdrf { pattern = '.*\\.sdrf\\.tsv$'; kind = 'dataset' }
      fasta { pattern = '.*\\.(fasta|fa)(\\.gz)?$'; kind = 'dataset' }
    }
  }

  output_artifacts {
    enabled = true
    key = [relativize: params.outdir]
    rules {
      mztab { pattern = '.*\\.mzTab$'; kind = 'dataset' }
      msstats_in { pattern = '.*msstats_in\\.csv$'; kind = 'dataset' }
      msstats_results { pattern = '.*/msstats/.*\\.(csv|tsv)$'; kind = 'dataset' }
      multiqc_report { pattern = '.*/pmultiqc/multiqc_report\\.html$'; kind = 'report' }
      // ... see full config for all rules
    }
  }
}
```

→ [Full config](https://github.com/laminlabs/nf-lamin/blob/main/examples/quantms/nextflow.config)
