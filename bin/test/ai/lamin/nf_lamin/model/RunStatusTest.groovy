package ai.lamin.nf_lamin.model

import spock.lang.Specification

/**
 * Test the RunStatus enum
 */
class RunStatusTest extends Specification {

    def "should have correct status codes"() {
        expect:
        RunStatus.SCHEDULED.code == -3
        RunStatus.RESTARTED.code == -2
        RunStatus.STARTED.code == -1
        RunStatus.COMPLETED.code == 0
        RunStatus.ERRORED.code == 1
        RunStatus.ABORTED.code == 2
    }

    def "should have correct descriptions"() {
        expect:
        RunStatus.SCHEDULED.description == "scheduled"
        RunStatus.RESTARTED.description == "re-started"
        RunStatus.STARTED.description == "started"
        RunStatus.COMPLETED.description == "completed"
        RunStatus.ERRORED.description == "errored"
        RunStatus.ABORTED.description == "aborted"
    }

    def "should have proper toString representation"() {
        expect:
        RunStatus.SCHEDULED.toString() == "scheduled (-3)"
        RunStatus.COMPLETED.toString() == "completed (0)"
        RunStatus.ERRORED.toString() == "errored (1)"
    }
}
