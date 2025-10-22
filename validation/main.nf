nextflow.preview.output = true

include { getRunUid; getTransformUid; getArtifactUrlByUid } from 'plugin/nf-lamin'

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

  // test artifact fetching
  def artPath = getArtifactUrlByUid('s3rtK8wIzJNKvg5Q')
  log.info "Artifact URL for UID 's3rtK8wIzJNKvg5Q': ${artPath}"

  def artPath2 = getArtifactUrlByUid('HOpnASIDDLx3pFYD0000')
  log.info "Artifact URL for UID 'HOpnASIDDLx3pFYD0000': ${artPath2}"

  // create output channel
  ch_out = Channel.of([
    [id: 'lamin_metadata', path: metadataFile]
  ])

  publish:
  output = ch_out
}

output {
  output {
    path '.'
  }
}
