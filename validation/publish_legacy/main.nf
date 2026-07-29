/*
 * nf-lamin publish validation workflow with the legacy DSL2 syntax parser.
 *
 * Publishes to a lamin:// target through publishDir, including a directory output and a
 * file large enough to go through the multipart upload path.
 */

include { getRunUid; getTransformUid } from 'plugin/nf-lamin'

/*
  Parameters
*/

// An artifact URI in lamin:// format, read back to check that reading still works
params.input = 'lamin://laminlabs/lamin-dev/artifact/CUrOAtaYX5OZDgcf'

// A lamin:// publish target, e.g. lamin://laminlabs/lamin-dev?prefix=nf-lamin-test
params.outputDir = null

// Size of the large file, in MiB. Above the multipart threshold this exercises the
// multipart upload path.
params.largeFileMiB = 0

/*
  Publishes a single file.
*/
process makeReport {
  publishDir { "${params.outputDir}/reports/${id}" }, mode: 'copy', overwrite: true

  input:
  tuple val(id), path(input)

  output:
  tuple val(id), path("report.json")

  script:
  def metadata = [
    id: id,
    runUid: getRunUid(),
    transformUid: getTransformUid(),
    inputFileSize: input.size()
  ]
  """
  cat > report.json << EOF
  ${groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(metadata))}
  EOF
  """
}

/*
  Publishes a whole directory, which takes a different route through PublishDir than a
  single file does.
*/
process makeBundle {
  publishDir { "${params.outputDir}/bundles" }, mode: 'copy', overwrite: true

  input:
  val id

  output:
  path "bundle_${id}"

  script:
  """
  mkdir -p bundle_${id}/nested
  echo "top level" > bundle_${id}/top.txt
  echo "nested" > bundle_${id}/nested/inner.txt
  """
}

/*
  Publishes a file big enough to trip the multipart threshold, when asked for.
*/
process makeLargeFile {
  publishDir { "${params.outputDir}/large" }, mode: 'copy', overwrite: true

  input:
  val sizeMiB

  output:
  path "large.bin"

  when:
  sizeMiB > 0

  script:
  """
  dd if=/dev/urandom of=large.bin bs=1M count=${sizeMiB} status=none
  """
}

workflow {
  main:

  if (!params.outputDir) {
    error "Set --output-dir to a lamin:// publish target, e.g. lamin://laminlabs/lamin-dev?prefix=nf-lamin-test"
  }

  // Reading through lamin:// must keep working alongside writing
  def artPath = file(params.input)
  log.info "Publishing to: ${params.outputDir}"
  log.info "Resolved input artifact: ${artPath.resolveToStorage()}"

  channel.fromList([["sample_1", artPath]])
    | makeReport
    | view { it -> "Published report: $it" }

  channel.of("1")
    | makeBundle
    | view { it -> "Published bundle: $it" }

  channel.of(params.largeFileMiB as Integer)
    | makeLargeFile
    | view { it -> "Published large file: $it" }
}
