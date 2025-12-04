include { getRunUid; getTransformUid; getArtifactFromUid } from 'plugin/nf-lamin'

process summarizeData {
  publishDir "${params.outputDir}/${id}", mode: 'copy', overwrite: true

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

workflow {
  main:

  // test artifact fetching
  def artPath = getArtifactFromUid('laminlabs', 'lamindata', 's3rtK8wIzJNKvg5Q')
  log.info "Artifact URL for UID 's3rtK8wIzJNKvg5Q': ${artPath}"

  // assumes the current instance is indeed laminlabs/lamindata
  def artPath2 = getArtifactFromUid('HOpnASIDDLx3pFYD0000')
  log.info "Artifact URL for UID 'HOpnASIDDLx3pFYD0000': ${artPath2}"

  // create output channel
  ch_out = Channel.fromList([
    ["lamin_metadata", file("gs://di-temporary-public/scratch/temp-bgzip/run_20251015_120418/run.bgzip.state.yaml")]
  ])
    | view{("Before publish: $it")}
    | summarizeData
    | view{("After publish: $it")}

  // publish:
  // output = ch_out
}

// TODO: revert this when workflow outputs are working again
// output {
//   output {
//     path '.'
//   }
// }
