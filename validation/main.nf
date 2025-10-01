include { laminRunMetadata; laminRunUid; laminTransformMetadata; laminTransformUid } from 'plugin/nf-lamin'

workflow {
  main:
  def runMeta = laminRunMetadata()
  if (!runMeta) {
      throw new IllegalStateException('laminRunMetadata() returned null. Ensure the plugin can reach LaminDB and the run was initialised.')
  }
  def transformMeta = laminTransformMetadata()
  if (!transformMeta) {
      throw new IllegalStateException('laminTransformMetadata() returned null. Ensure the plugin can reach LaminDB and the transform was initialised.')
  }
  def runUid = laminRunUid()
  if (!runUid) {
      throw new IllegalStateException('laminRunUid() returned null. Ensure the plugin has created or resolved the run in LaminDB.')
  }
  def transformUid = laminTransformUid()
  if (!transformUid) {
      throw new IllegalStateException('laminTransformUid() returned null. Ensure the plugin has created or resolved the transform in LaminDB.')
  }
  log.info "Validated Lamin run ${runUid} for transform ${transformUid}"

  // create metadata file
  def metadata = [
    runUid: runUid,
    transformUid: transformUid,
    runMetadata: runMeta,
    transformMetadata: transformMeta
  ]
  def metadataFile = File.createTempFile('lamin_metadata_', '.json')
  metadataFile.text = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(metadata))
  log.info "Wrote metadata to ${metadataFile.absolutePath}"

  // create output channel
  out_ch = Channel.of(metadataFile)

  publish:
  output = out_ch
}

output {
  output {
    path '.'
  }
}
