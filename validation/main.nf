include { getRunUid; getTransformUid; getArtifactFromUid } from 'plugin/nf-lamin'

params.artifactUri = 'lamin://laminlabs/lamindata/artifact/s3rtK8wIzJNKvg5Q'  // full artifact URI (a small text file)
params.artifactUidOnCurrentInstance = 'HOpnASIDDLx3pFYD0000'                  // same artifact UID for current instance lookup

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

  // test artifact fetching via lamin:// URI
  def artPath = file(params.artifactUri)
  log.info "Resolved artifact URL for '${params.artifactUri}': ${artPath.resolveToStorage()}"
  log.info "Artifact path class: ${artPath.class.name}"

  // Test that we can actually read the file contents via lamin:// path
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

  // Test artifact fetching via getArtifactFromUid (returns S3 path directly)
  def artPath2 = getArtifactFromUid(params.artifactUidOnCurrentInstance)
  log.info "Artifact via getArtifactFromUid('${params.artifactUidOnCurrentInstance}'): ${artPath2}"
  log.info "Artifact path2 class: ${artPath2.class.name}"

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
