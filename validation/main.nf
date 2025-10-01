include { laminRunMetadata; laminRunUid; laminTransformMetadata; laminTransformUid } from 'plugin/nf-lamin'

params.outdir = "${projectDir}/results"

process emit_metadata {
    tag { meta.runUid }
    label 'lamin_validation'
    publishDir "${params.outdir}/validation", mode: 'copy', overwrite: true

    input:
    tuple val(runUid), val(meta)

    output:
    path 'lamin-validation.json'

    script:
    def payload = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(meta))
    """
cat <<'EOF' > lamin-validation.json
${payload}
EOF
    """
}

workflow {
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

  def metadata = [
    runUid: runUid,
    transformUid: transformUid,
    runMetadata: runMeta,
    transformMetadata: transformMeta
  ]

  Channel.of([runUid, metadata])
    | emit_metadata
}
