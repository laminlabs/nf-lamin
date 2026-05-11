/*
 * nf-lamin validation workflow with Nextflow 26.04+ (strict syntax)
 *
 * Demonstrates typed params, typed processes, typed workflows, and the
 * workflow output block (new in 26.04) together with the nf-lamin plugin.
 */

nextflow.enable.types = true

include { getRunUid; getTransformUid } from 'plugin/nf-lamin'

/*
  Typed parameters (Nextflow 26.04+).
  Use -output-dir <dir> to control the output directory.
*/
params {
  // An artifact URI in lamin:// format
  input: String = 'lamin://laminlabs/lamin-dev/artifact/CUrOAtaYX5OZDgcf'
}

/*
  Record types (Nextflow 26.04+)
*/
record InputSample {
  id: String
  artifact: Path
}

record SummarizeResult {
  id: String
  result: Path
}

/*
  Typed process: destructured record input, record output (Nextflow 26.04+).
*/
process summarizeData {
  input:
  record(
    id: String,
    artifact: Path
  )

  output:
  record(
    id: id,
    result: file('output.json')
  )

  script:
  def runUid = getRunUid()
  def transformUid = getTransformUid()
  def metadata = [
    id: id,
    runUid: runUid,
    transformUid: transformUid,
    inputFileSize: artifact.size()
  ]
  """
  cat > output.json << EOF
  ${groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(metadata))}
  EOF
  """
}

/*
  Typed named workflow: typed take and emit (Nextflow 26.04+).
*/
workflow SUMMARIZE_ARTIFACTS {
  take:
  samples: Channel<InputSample>

  main:
  results = summarizeData(samples)

  emit:
  results: Channel<SummarizeResult> = results
}

/*
  Entry workflow with publish section and typed workflow invocation.
*/
workflow {
  main:

  // Test artifact fetching via lamin:// URI
  def artPath = file(params.input)
  log.info "Resolved artifact URL for '${params.input}': ${artPath.resolveToStorage()}"
  log.info "Artifact path class: ${artPath.class.name}"

  try {
    def artSize = artPath.size()
    log.info "Artifact size via lamin:// path: ${artSize} bytes"
    if (artSize > 0 && artSize < 1000) {
      def artContent = artPath.text.take(100)
      log.info "Artifact content preview: ${artContent}..."
    }
  } catch (Exception e) {
    log.error "Failed to read artifact via lamin:// path: ${e.message}"
  }

  // Construct typed record channel and run named workflow
  ch_input = channel.of(record(id: "artifact1", artifact: artPath))
  out = SUMMARIZE_ARTIFACTS(ch_input)

  publish:
  results = out.results
}

/*
  Output block: publishes workflow results via -output-dir (Nextflow 26.04+).
  Each result is published under a subdirectory named by the record's id field.
*/
output {
  results {
    path { r -> "${r.id}/" }
    mode 'copy'
  }
}
