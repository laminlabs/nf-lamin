package ai.lamin.nf_lamin

import ai.lamin.nf_lamin.instance.Instance
import ai.lamin.nf_lamin.model.RunStatus
import nextflow.Session
import nextflow.exception.AbortSignalException
import spock.lang.Specification

import java.lang.reflect.Field
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import sun.misc.Signal

class LaminRunManagerTest extends Specification {

    def setup() {
        LaminRunManager.instance.reset()
    }

    private static void injectField(Object target, String fieldName, Object value) {
        Field field = target.class.getDeclaredField(fieldName)
        field.accessible = true
        field.set(target, value)
    }

    def 'stores and exposes transform and run metadata'() {
        given:
        Map<String, Object> transform = [uid: 'T123', id: 42]
        Map<String, Object> run = [uid: 'R456', id: 99]

        when:
        LaminRunManager.instance.updateTransform(transform)
        LaminRunManager.instance.updateRun(run)

        then:
        LaminRunManager.instance.transform.uid == 'T123'
        LaminRunManager.instance.run.uid == 'R456'

        and:
        def extension = new LaminExtension()
        extension.getTransformUid() == 'T123'
        extension.getRunUid() == 'R456'
    }

    def 'reset clears stored state'() {
        given:
        LaminRunManager.instance.updateRun([uid: 'R999'])

        when:
        LaminRunManager.instance.reset()

        then:
        LaminRunManager.instance.run == null
        new LaminExtension().getRunUid() == null
    }

    def 'fetchOrCreateArtifact passes branch_id and space_id when resolved'() {
        given:
        def manager = LaminRunManager.instance
        def mockInstance = Mock(Instance)
        def config = new LaminConfig([instance: 'testorg/testinst', api_key: 'test-key'])
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', config)
        injectField(manager, 'resolvedBranchId', 7)
        injectField(manager, 'resolvedSpaceId', 3)

        def mockPath = Mock(Path)
        mockPath.toUri() >> new URI('s3://test-bucket/test-file.txt')

        mockInstance.getArtifactByPath('s3://test-bucket/test-file.txt') >> null
        mockInstance.getOwner() >> 'testorg'
        mockInstance.getName() >> 'testinst'

        when:
        Map<String, Object> result = manager.fetchOrCreateArtifact([path: mockPath])

        then:
        1 * mockInstance.createArtifact({ Map args ->
            args.get('branch_id') == 7 &&
            args.get('space_id') == 3 &&
            args.get('path') == 's3://test-bucket/test-file.txt'
        }) >> [uid: 'testuid1234567890ab', branch: 7.0]
        result != null
    }

    def 'fetchOrCreateArtifact omits branch_id and space_id when not resolved'() {
        given:
        def manager = LaminRunManager.instance
        def mockInstance = Mock(Instance)
        def config = new LaminConfig([instance: 'testorg/testinst', api_key: 'test-key'])
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', config)
        // resolvedBranchId and resolvedSpaceId remain null from reset() in setup()

        def mockPath = Mock(Path)
        mockPath.toUri() >> new URI('s3://test-bucket/test-file.txt')

        mockInstance.getArtifactByPath('s3://test-bucket/test-file.txt') >> null
        mockInstance.getOwner() >> 'testorg'
        mockInstance.getName() >> 'testinst'

        when:
        Map<String, Object> result = manager.fetchOrCreateArtifact([path: mockPath])

        then:
        1 * mockInstance.createArtifact({ Map args ->
            !args.containsKey('branch_id') &&
            !args.containsKey('space_id')
        }) >> [uid: 'testuid1234567890ab']
        result != null
    }

    def 'createInputArtifactsAsync returns before worker finishes (non-blocking)'() {
        given:
        def manager = LaminRunManager.instance
        def mockInstance = Mock(Instance)
        def config = new LaminConfig([instance: 'org/inst', api_key: 'key'])
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', config)
        injectField(manager, 'run', [uid: 'R1', id: 1])

        def workerStarted = new CountDownLatch(1)
        def releaseWorker = new CountDownLatch(1)
        mockInstance.createArtifact(_) >> {
            workerStarted.countDown()
            releaseWorker.await(5, TimeUnit.SECONDS)
            [uid: 'A1', run: 1]
        }

        def mockPath = Mock(Path)
        mockPath.toUri() >> new URI('s3://bucket/file.txt')

        when:
        long before = System.nanoTime()
        manager.createInputArtifactsAsync('test-task', [mockPath])
        long elapsedMs = (System.nanoTime() - before) / 1_000_000L

        then: 'method returned quickly without waiting for the API call'
        elapsedMs < 3000

        and: 'worker actually started in background'
        workerStarted.await(5, TimeUnit.SECONDS)

        cleanup:
        releaseWorker.countDown()
    }

    def 'determineRunStatus maps session state to run status'() {
        given:
        def manager = LaminRunManager.instance
        def session = Stub(Session) {
            isSuccess() >> success
            isCancelled() >> cancelled
            getError() >> error
        }
        injectField(manager, 'session', session)

        expect:
        manager.determineRunStatus() == expected

        where:
        scenario                       | success | cancelled | error                                          || expected
        'successful run'               | true    | false     | null                                           || RunStatus.COMPLETED
        'graceful cancel flag'         | false   | true      | null                                           || RunStatus.ABORTED
        'SIGTERM from Seqera cancel'   | false   | false     | new AbortSignalException(new Signal('TERM'))   || RunStatus.ABORTED
        'SIGINT from local Ctrl+C'     | false   | false     | new AbortSignalException(new Signal('INT'))    || RunStatus.ABORTED
        'task failure'                 | false   | false     | new RuntimeException('task failed')            || RunStatus.ERRORED
        'error without cause'          | false   | false     | null                                           || RunStatus.ERRORED
    }
}
