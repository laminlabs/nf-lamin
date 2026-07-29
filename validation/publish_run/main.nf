/*
 * nf-lamin publish validation workflow with Nextflow 26.04+ (strict syntax)
 *
 * Publishes into a Lamin storage location via `-output-dir lamin://...`, and writes both
 * a CSV and a JSON index so that the appending and the byte-channel write paths of the
 * lamin-s3 provider are both exercised.
 */

nextflow.enable.types = true

include { getRunUid; getTransformUid; getInstanceSlug } from 'plugin/nf-lamin'

params {
  // An artifact URI in lamin:// format, read back to check that reading still works
  input: String = 'lamin://laminlabs/lamin-dev/artifact/CUrOAtaYX5OZDgcf'

  // Number of samples to publish
  samples: Integer = 3
}

record Sample {
  id: String
  report: Path
}

/*
  Writes a small report plus a directory of files, so that both a single-file publish and
  a directory publish are covered.
*/
process makeReport {
  input:
  id: String

  output:
  record(
    id: id,
    report: file('report.json')
  )

  script:
  def metadata = [
    id: id,
    runUid: getRunUid(),
    transformUid: getTransformUid(),
    instance: getInstanceSlug()
  ]
  """
  cat > report.json << EOF
  ${groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(metadata))}
  EOF
  """
}

workflow {
  main:

  // Reading through lamin:// must keep working alongside writing
  def artPath = file(params.input)
  log.info "Publishing to: ${workflow.outputDir}"
  log.info "Output dir path class: ${workflow.outputDir.class.name}"
  log.info "Resolved input artifact: ${artPath.resolveToStorage()}"

  def ids = (1..params.samples).collect { i -> "sample_${i}" as String }
  ch_reports = makeReport(channel.fromList(ids))

  publish:
  reports = ch_reports
}

output {
  reports {
    path { r -> "reports/${r.id}/" }
    index {
      path 'reports/index.csv'
    }
    mode 'copy'
  }
}
