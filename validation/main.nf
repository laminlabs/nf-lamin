include { getRunUid; getTransformUid; getArtifactFromUid } from 'plugin/nf-lamin'

params.artifactUri = 'lamin://laminlabs/lamindata/artifacts/s3rtK8wIzJNKvg5Q'  // full artifact URI
params.artifactUidOnCurrentInstance = 'HOpnASIDDLx3pFYD0000'                   // artifact UID for current instance lookup

process publishData {
  publishDir "${params.outputDir}/${id}", mode: 'copy', overwrite: true

  input:
  tuple val(id), path(x)

  output:
  tuple val(id), path("$x")

  script:
  """
  echo "Publishing data for ${id}"
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
  def metadataFile = File.createTempFile('lamin_metadata_', '.json').toPath()
  metadataFile.text = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(metadata))
  log.info "Wrote metadata to ${metadataFile}"

  // test artifact fetching
  def artifactUriMatcher = (params.artifactUri =~ /^lamin:\/\/([^\/]+)\/([^\/]+)\/artifacts?\/(.+)$/)
  if (!artifactUriMatcher.matches()) {
      throw new IllegalArgumentException("Invalid artifact URI format: ${params.artifactUri}")
  }
  def (_, instOwner, instName, artifactUid) = artifactUriMatcher[0]
  def artPath = getArtifactFromUid(instOwner, instName, artifactUid)
  log.info "Artifact URL for '${params.artifactUri}': ${artPath}"

  // assumes the current instance is indeed laminlabs/lamindata
  def artPath2 = getArtifactFromUid(params.artifactUidOnCurrentInstance)
  log.info "Artifact URL for UID '${params.artifactUidOnCurrentInstance}': ${artPath2}"

  // create output channel
  ch_out = Channel.fromList([
    // [id: 'lamin_metadata', path: metadataFile]
    ["lamin_metadata", metadataFile]
  ])
    | view{it -> "Before publish: $it"}
    | publishData
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
