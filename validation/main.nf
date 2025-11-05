include { getRunUid; getTransformUid } from 'plugin/nf-lamin'

process publishData {
  publishDir "${params.outputDir}/${id}", mode: 'copy', overwrite: true

  input:
  tuple val(id), path(x)

  output:
  tuple val(id), path("$x")

  script:
  """
  echo "Publishing data for ${id}
  """
}

workflow {
  main:
  def runUid = getRunUid()
  if (!runUid) {
      throw new IllegalStateException('getRunUid() returned null. Ensure the plugin has created or resolved the run in LaminDB.')
  }
  def transformUid = getTransformUid()
  if (!transformUid) {
      throw new IllegalStateException('getTransformUid() returned null. Ensure the plugin has created or resolved the transform in LaminDB.')
  }
  log.info "Validated Lamin run ${runUid} for transform ${transformUid}"

  // create metadata file
  def metadata = [
    runUid: runUid,
    transformUid: transformUid
  ]
  def metadataFile = File.createTempFile('lamin_metadata_', '.json')
  metadataFile.text = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(metadata))
  log.info "Wrote metadata to ${metadataFile.absolutePath}"

  // create output channel
  ch_out = Channel.of([
    // [id: 'lamin_metadata', path: metadataFile]
    ["lamin_metadata", metadataFile]
  ])
    | publishData

  // publish:
  // output = ch_out
}

// TODO: revert this when workflow outputs are working again
// output {
//   output {
//     path '.'
//   }
// }
