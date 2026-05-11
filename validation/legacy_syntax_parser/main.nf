include { getRunUid; getTransformUid } from 'plugin/nf-lamin'

/*
  Parameters
*/

// An artifact URI in lamin:// format
params.input = 'lamin://laminlabs/lamin-dev/artifact/CUrOAtaYX5OZDgcf'

// Output directory
params.outputDir = "output"

/*
  Process to summarize data and generate output.json with metadata
*/
process summarizeData {
  publishDir { "${params.outputDir}/${id}" }, mode: 'copy', overwrite: true

  input:
  tuple val(id), path(input)

  output:
  tuple val(id), path("output.json")

  script:
  def runUid = getRunUid()
  def transformUid = getTransformUid()

  def metadata = [
    id: id,
    runUid: runUid,
    transformUid: transformUid,
    inputFileSize: input.size()
  ]
  """
  cat > output.json << EOF
  ${groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(metadata))}
  EOF
  """
}

/*
  Main workflow
*/
workflow {
  main:

  // test artifact fetching via lamin:// URI
  def artPath1 = file(params.input)
  log.info "Resolved artifact URL for '${params.input}': ${artPath1.resolveToStorage()}"
  log.info "Artifact path class: ${artPath1.class.name}"

  // Test that we can actually read the file contents via lamin:// path
  try {
    def artSize = artPath1.size()
    log.info "Artifact size via lamin:// path: ${artSize} bytes"
    if (artSize > 0 && artSize < 1000) {
      def artContent = artPath1.text.take(100)
      log.info "Artifact content preview: ${artContent}..."
    }
  } catch (Exception e) {
    log.error "Failed to read artifact via lamin:// path: ${e.message}"
  }

  // create output channel
  ch_out = channel.fromList([
    ["artifact1", artPath1]
  ])
    | view{it -> "Before publish: $it"}
    | summarizeData
    | view{it -> "After publish: $it"}

  // publish:
  // output = ch_out
}

// TODO: revert this when workflow outputs are working again
// output {
//   output {
//     path '.'
//   }
// }
