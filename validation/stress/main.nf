/*
 * nf-lamin API stress-test workflow.
 *
 * Mints many artifacts cheaply so the Lamin API (artifact registration) can be
 * load-tested without waiting on real compute.
 *
 * Tunable load shape (all overridable on the CLI as --<name> <value>):
 *   --n_files        total number of artifacts to create               (default 1000)
 *   --files_per_task number of files generated per Nextflow task        (default 100)
 *                    (controls Nextflow task overhead vs artifact count;
 *                     n_tasks = ceil(n_files / files_per_task))
 *   --file_size      size of each artifact in bytes                     (default 1024)
 *   --time_spread    spread task completion across this many seconds     (default 0)
 *                    (0 = burst: all tasks finish ~together; >0 = staggered
 *                     so registration load is spread over the window)
 *   --max_forks      max concurrent generator tasks                      (default 16)
 *
 * The publish destination is set with the -output-dir CLI option (Nextflow's
 * workflow output block), not a param.
 */

/*
  Parameters
*/
params.n_files        = 1000
params.files_per_task = 100
params.file_size      = 1024
params.time_spread    = 0
params.max_forks      = 16

/*
  Generate a batch of `count` random files, each `file_size` bytes.

  `delay` staggers task completion to spread registration load over time.
  File names embed the run uid, batch id, index and random bytes so artifacts
  are unique within and across runs (no path or content collisions).
*/
process generateArtifacts {
  tag "batch_${batch_id}"
  maxForks params.max_forks

  input:
  tuple val(batch_id), val(count), val(delay), val(run_uid)

  output:
  path "artifact_*.bin"

  script:
  """
  # Stagger completion to spread API registration load over --time_spread seconds.
  sleep ${delay}

  for i in \$(seq 1 ${count}); do
    suffix=\$(head -c 6 /dev/urandom | od -An -tx1 | tr -d ' \\n')
    head -c ${params.file_size} /dev/urandom \
      > "artifact_${run_uid}_b${batch_id}_f\${i}_\${suffix}.bin"
  done
  """
}

/*
  Main workflow: split n_files into batches and dispatch them.
*/
workflow {
  main:

  def runUid       = workflow.sessionId.toString().take(8)

  def nFiles       = params.n_files as int
  def perTask      = Math.max(1, params.files_per_task as int)
  def spread       = params.time_spread as double
  def nBatches     = Math.ceil(nFiles / (perTask as double)) as int

  log.info "nf-lamin stress test: run=${runUid}"
  log.info "  n_files=${nFiles}  files_per_task=${perTask}  n_tasks=${nBatches}"
  log.info "  file_size=${params.file_size}B  time_spread=${spread}s  max_forks=${params.max_forks}"
  log.info "  estimated total bytes=${nFiles * (params.file_size as long)}"

  // Build [batch_id, count, delay, run_uid] for each batch.
  def batches = (0..<nBatches).collect { b ->
    def count = Math.min(perTask, nFiles - b * perTask)
    // Spread batch completion evenly across the time_spread window.
    def delay = (nBatches > 1 && spread > 0) ? (b * spread / (nBatches - 1)) : 0d
    tuple(b, count, (Math.round(delay * 10) / 10.0), runUid)
  }

  ch_artifacts = channel.fromList(batches)
    | generateArtifacts
    | flatten

  ch_artifacts
    | count
    | view { n -> "Generated ${n} artifact files for registration." }

  publish:
  artifacts = ch_artifacts
}

/*
  Output block: publishes each generated artifact via -output-dir (Nextflow 26.04+).
  nf-lamin registers each published file as an output artifact.
*/
output {
  artifacts {
    path '.'
    mode 'copy'
  }
}
