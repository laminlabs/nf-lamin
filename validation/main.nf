include { getRunUid; getTransformUid; getArtifactFromUid } from 'plugin/nf-lamin'

params.artifactUri = 'lamin://laminlabs/lamindata/artifact/s3rtK8wIzJNKvg5Q'  // full artifact URI (a small text file)
params.artifactUidOnCurrentInstance = 'HOpnASIDDLx3pFYD0000'                  // same artifact UID for current instance lookup

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
    ["lamin_metadata", file("gs://di-temporary-public/scratch/temp-bgzip/run_20251015_120418/run.bgzip.state.yaml")]
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
