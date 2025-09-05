package nextflow.lamin.model

import groovy.transform.CompileStatic

/**
 * Status codes for workflow runs in Lamin
 *
 * See https://github.com/laminlabs/lamindb/blob/768c66c6347e1a098ffd0b9e03f77685c47f679c/lamindb/models/run.py#L323
 *
 * SCHEDULED: When workflow is created (onFlowCreate)
 * RESTARTED: For workflow restarts (future use)
 * STARTED: When workflow begins execution (onFlowBegin)
 * COMPLETED: When workflow completes successfully (onFlowComplete)
 * ERRORED: When workflow encounters an error (onFlowError)
 * ABORTED: When workflow is aborted/cancelled (future use)
 */
@CompileStatic
enum RunStatus {
    SCHEDULED(-3, "scheduled"),
    RESTARTED(-2, "re-started"),
    STARTED(-1, "started"),
    COMPLETED(0, "completed"),
    ERRORED(1, "errored"),
    ABORTED(2, "aborted")

    final int code
    final String description

    RunStatus(int code, String description) {
        this.code = code
        this.description = description
    }

    @Override
    String toString() {
        return "${description} (${code})"
    }
}
