package ai.lamin.nf_lamin

import ai.lamin.lamin_api_client.ApiException
import ai.lamin.nf_lamin.instance.Instance
import ai.lamin.nf_lamin.model.ArtifactStats
import ai.lamin.nf_lamin.util.ArtifactStatsResolver
import ai.lamin.nf_lamin.model.ArtifactAnnotation
import ai.lamin.nf_lamin.model.RunStatus
import ai.lamin.nf_lamin.nio.LaminFileSystemProvider
import ai.lamin.nf_lamin.nio.LaminS3FileSystem
import ai.lamin.nf_lamin.nio.LaminS3FileSystemProvider
import ai.lamin.nf_lamin.nio.LaminS3Path
import ai.lamin.nf_lamin.nio.LaminStorageTarget
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client
import nextflow.Session
import nextflow.exception.AbortSignalException
import nextflow.script.WorkflowMetadata
import spock.lang.Specification

import java.lang.reflect.Field
import java.net.URI
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.LongSupplier
import sun.misc.Signal

class LaminRunManagerTest extends Specification {

    def setup() {
        LaminRunManager.instance.reset()
        // keep the mocked s3 paths of these tests away from real credentials and S3
        injectField(LaminRunManager.instance, 'statsResolver', Stub(ArtifactStatsResolver) { resolve(_, _) >> null })
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

    private LaminS3Path publishedPath(String key) {
        def fs = new LaminS3FileSystem(Mock(LaminS3FileSystemProvider), 's3://bucket/JwMEKs04D9WJ', Mock(AwsS3Client))
        return new LaminS3Path(fs, key)
    }

    /** A lamin:// provider that answers every publish-target lookup with the same target. */
    private static class StubLaminFileSystemProvider extends LaminFileSystemProvider {
        LaminStorageTarget target

        @Override
        LaminStorageTarget publishTargetFor(Path path) { target }
    }

    def 'fetchOrCreateArtifact takes the space from the storage location a published file resolved to'() {
        given:
        def manager = LaminRunManager.instance
        def mockInstance = Mock(Instance)
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', new LaminConfig([instance: 'testorg/testinst', api_key: 'test-key']))
        injectField(manager, 'resolvedSpaceId', 3)
        def target = new LaminStorageTarget(storageRoot: 's3://bucket/JwMEKs04D9WJ', storageUid: 'JwMEKs04D9WJ', spaceId: 5)
        injectField(manager, 'laminFileSystemProvider', new StubLaminFileSystemProvider(target: target))
        def path = publishedPath('JwMEKs04D9WJ/results/a.txt')
        mockInstance.getArtifactByPath(_) >> null

        when:
        manager.fetchOrCreateArtifact([path: path])

        then:
        1 * mockInstance.createArtifact({ Map args ->
            args.get('space_id') == 5 && args.get('path') == 's3://bucket/JwMEKs04D9WJ/results/a.txt'
        }) >> [uid: 'testuid1234567890ab']
    }

    def 'fetchOrCreateArtifact keeps the configured space when the storage location has none'() {
        given:
        def manager = LaminRunManager.instance
        def mockInstance = Mock(Instance)
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', new LaminConfig([instance: 'testorg/testinst', api_key: 'test-key']))
        injectField(manager, 'resolvedSpaceId', 3)
        def target = new LaminStorageTarget(storageRoot: 's3://bucket/JwMEKs04D9WJ', storageUid: 'JwMEKs04D9WJ')
        injectField(manager, 'laminFileSystemProvider', new StubLaminFileSystemProvider(target: target))
        def path = publishedPath('JwMEKs04D9WJ/results/a.txt')
        mockInstance.getArtifactByPath(_) >> null

        when:
        manager.fetchOrCreateArtifact([path: path])

        then:
        1 * mockInstance.createArtifact({ Map args -> args.get('space_id') == 3 }) >> [uid: 'testuid1234567890ab']
    }

    def 'an index file announced by onWorkflowOutput is registered when it is published'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = annotatingManager(mockInstance)
        def index = remotePath('s3://bucket/results/reports/index.csv')
        mockInstance.getArtifactByPath(_) >> null

        when: 'the output block is announced, before the index file exists'
        manager.rememberOutputName(index, 'reports')

        then:
        0 * mockInstance.createArtifact(_)

        when: 'the index file is published'
        manager.createOutputArtifactOnFilePublish(null, index, null)

        then:
        1 * mockInstance.createArtifact({ Map args -> (args.description as String).contains("'reports'") }) >> [uid: 'A1', id: 11, run: 1]
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

    def 'startRun omits the reference when not running on Seqera Platform'() {
        given:
        def manager = LaminRunManager.instance
        def mockInstance = Mock(Instance)
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', new LaminConfig([instance: 'testorg/testinst', api_key: 'test-key']))
        injectField(manager, 'run', [uid: 'R456', id: 99] as Map<String, Object>)

        // Stub(WorkflowMetadata) has no getPlatform(), like Nextflow 25.10
        def metadata = Stub(WorkflowMetadata) {
            getStart() >> OffsetDateTime.now()
        }
        injectField(manager, 'session', Stub(Session) { getWorkflowMetadata() >> metadata })

        when:
        manager.startRun()

        then:
        1 * mockInstance.updateRecord({ Map args ->
            Map data = args.data as Map
            !data.containsKey('reference') &&
            !data.containsKey('reference_type')
        }) >> [uid: 'R456']
    }

    def 'startRun stores the watch url and reference type when running on Seqera Platform'() {
        given:
        def manager = LaminRunManager.instance
        def mockInstance = Mock(Instance)
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', new LaminConfig([instance: 'testorg/testinst', api_key: 'test-key']))
        injectField(manager, 'run', [uid: 'R456', id: 99] as Map<String, Object>)

        String watchUrl = 'https://cloud.seqera.io/orgs/o/workspaces/w/watch/b0siCig3qoUvZ'
        def metadata = new MetadataWithPlatform(platform: new PlatformWithUrl(workflowUrl: watchUrl))
        injectField(manager, 'session', Stub(Session) { getWorkflowMetadata() >> metadata })

        when:
        manager.startRun()

        then:
        1 * mockInstance.updateRecord({ Map args ->
            Map data = args.data as Map
            data.get('reference') == watchUrl &&
            data.get('reference_type') == 'Seqera'
        }) >> [uid: 'R456']
    }

    def 'startRun still marks the run as started when the reference cannot be read'() {
        given:
        def manager = LaminRunManager.instance
        def mockInstance = Mock(Instance)
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', new LaminConfig([instance: 'testorg/testinst', api_key: 'test-key']))
        injectField(manager, 'run', [uid: 'R456', id: 99] as Map<String, Object>)

        // fail the second metadata read -- the reference lookup -- with a linkage error
        int calls = 0
        def session = Stub(Session) {
            getWorkflowMetadata() >> {
                if (calls++ > 0) {
                    throw new NoClassDefFoundError('nextflow/script/PlatformMetadata')
                }
                return Stub(WorkflowMetadata) { getStart() >> OffsetDateTime.now() }
            }
        }
        injectField(manager, 'session', session)

        when:
        manager.startRun()

        then:
        noExceptionThrown()
        1 * mockInstance.updateRecord({ Map args ->
            Map data = args.data as Map
            data.containsKey('_status_code') &&
            !data.containsKey('reference')
        }) >> [uid: 'R456']
    }

    /** Mimics Nextflow >= 26.04, where WorkflowMetadata exposes getPlatform(). */
    static class MetadataWithPlatform extends WorkflowMetadata {

        Object platform

    }

    /** Mimics nextflow.script.PlatformMetadata. */
    static class PlatformWithUrl {

        Object workflowUrl

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

    private LaminRunManager inputTrackingManager(Instance mockInstance) {
        def manager = LaminRunManager.instance
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', new LaminConfig([instance: 'org/inst', api_key: 'key']))
        injectField(manager, 'run', [uid: 'R1', id: 1])
        return manager
    }

    private Path sharedInput() {
        def path = Mock(Path)
        path.toUri() >> new URI('s3://bucket/file.txt')
        return path
    }

    def 'registers an input used by several tasks once'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = inputTrackingManager(mockInstance)

        when:
        manager.createInputArtifactsAsync('first', [sharedInput()])
        manager.createInputArtifactsAsync('second', [sharedInput()])
        manager.awaitArtifactTasks()

        then:
        1 * mockInstance.createArtifact(_) >> [uid: 'A1', id: 11, run: 7]
        1 * mockInstance.upsertRecord(_)
    }

    def 'tries an input again when a later task uses it after it failed'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = inputTrackingManager(mockInstance)
        injectField(manager, 'artifactExecutor', java.util.concurrent.Executors.newFixedThreadPool(1))

        when:
        manager.createInputArtifactsAsync('first', [sharedInput()])
        manager.createInputArtifactsAsync('second', [sharedInput()])
        manager.awaitArtifactTasks()

        then:
        1 * mockInstance.createArtifact(_) >> { throw new RuntimeException('hub down') }
        1 * mockInstance.createArtifact(_) >> [uid: 'A1', id: 11, run: 7]
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
        'errorStrategy finish failure' | false   | true      | null                                           || RunStatus.ERRORED
        'SIGTERM from Seqera cancel'   | false   | false     | new AbortSignalException(new Signal('TERM'))   || RunStatus.ABORTED
        'SIGINT from local Ctrl+C'     | false   | false     | new AbortSignalException(new Signal('INT'))    || RunStatus.ABORTED
        'task failure'                 | false   | false     | new RuntimeException('task failed')            || RunStatus.ERRORED
        'error without cause'          | false   | false     | null                                           || RunStatus.ERRORED
    }

    // ========== annotateArtifact ==========

    /**
     * A remote path, since local paths are never tracked as artifacts.
     */
    private Path remotePath(String uri) {
        def path = Stub(Path)
        path.toUri() >> new URI(uri)
        path.toAbsolutePath() >> path
        path.normalize() >> path
        return path
    }

    private static LaminRunManager annotatingManager(Instance mockInstance) {
        def manager = LaminRunManager.instance
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', new LaminConfig([instance: 'org/inst', api_key: 'key']))
        injectField(manager, 'run', [uid: 'R1', id: 1])
        return manager
    }

    private static Set<String> readKeys(String fieldName) {
        def field = LaminRunManager.getDeclaredField(fieldName)
        field.accessible = true
        def value = field.get(LaminRunManager.instance)
        return (value instanceof Map ? (value as Map).keySet() : value) as Set<String>
    }

    def 'applies an annotation registered before the file is published'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = annotatingManager(mockInstance)
        def source = remotePath('s3://bucket/work/output.json')
        def target = remotePath('s3://bucket/results/output.json')

        mockInstance.getArtifactByPath(_) >> null
        mockInstance.createArtifact(_) >> [uid: 'A1', id: 11, run: 1]
        mockInstance.getRecord(_) >> [uid: 'ulab1234', id: 5]

        when: 'the workflow annotates the file it holds, before it is published'
        manager.registerAnnotation(source, ArtifactAnnotation.fromMap([
            kind: 'dataset',
            description: 'Summary',
            ulabel_uids: ['ulab1234']
        ]))
        manager.createOutputArtifactOnFilePublish(source, target, null)

        then:
        1 * mockInstance.updateRecord({ Map args ->
            args.uid == 'A1' && args.data.kind == 'dataset' && args.data.description == 'Summary'
        })
        1 * mockInstance.upsertRecord({ Map args ->
            args.modelName == 'artifactulabel' && args.data.artifact_id == 11 && args.data.ulabel_id == 5
        })
    }

    def 'applies an annotation registered after the file was published'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = annotatingManager(mockInstance)
        def source = remotePath('s3://bucket/work/output.json')
        def target = remotePath('s3://bucket/results/output.json')

        mockInstance.getArtifactByPath(_) >> null
        mockInstance.createArtifact(_) >> [uid: 'A1', id: 11, run: 1]

        when: 'publishing wins the race with the map operator'
        manager.createOutputArtifactOnFilePublish(source, target, null)
        manager.registerAnnotation(source, ArtifactAnnotation.fromMap([kind: 'dataset']))
        manager.awaitArtifactTasks()

        then:
        1 * mockInstance.updateRecord({ Map args -> args.uid == 'A1' && args.data.kind == 'dataset' })
    }

    def 'annotates an artifact under the path it was published to'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = annotatingManager(mockInstance)
        def source = remotePath('s3://bucket/work/output.json')
        def target = remotePath('s3://bucket/results/output.json')

        mockInstance.getArtifactByPath(_) >> null
        mockInstance.createArtifact(_) >> [uid: 'A1', id: 11, run: 1]

        when:
        manager.registerAnnotation(target, ArtifactAnnotation.fromMap([kind: 'dataset']))
        manager.createOutputArtifactOnFilePublish(source, target, null)

        then:
        1 * mockInstance.updateRecord({ Map args -> args.uid == 'A1' })
    }

    def 'applies every annotation registered for the same file'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = annotatingManager(mockInstance)
        def source = remotePath('s3://bucket/work/output.json')
        def target = remotePath('s3://bucket/results/output.json')

        mockInstance.getArtifactByPath(_) >> null
        mockInstance.createArtifact(_) >> [uid: 'A1', id: 11, run: 1]
        mockInstance.getRecord(_) >> [uid: 'ulab1234', id: 5]

        when:
        manager.registerAnnotation(source, ArtifactAnnotation.fromMap([kind: 'dataset']))
        manager.registerAnnotation(source, ArtifactAnnotation.fromMap([ulabel_uids: ['ulab1234']]))
        manager.createOutputArtifactOnFilePublish(source, target, null)

        then:
        1 * mockInstance.updateRecord({ Map args -> args.data.kind == 'dataset' })
        1 * mockInstance.upsertRecord({ Map args -> args.modelName == 'artifactulabel' })
    }

    def 'annotates an input artifact'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = annotatingManager(mockInstance)
        def input = remotePath('s3://bucket/inputs/reads.fastq')

        mockInstance.getArtifactByPath(_) >> null
        mockInstance.createArtifact(_) >> [uid: 'A2', id: 22, run: 1]

        when:
        manager.registerAnnotation(input, ArtifactAnnotation.fromMap([description: 'Raw reads']))
        manager.createInputArtifact(input)

        then:
        1 * mockInstance.updateRecord({ Map args -> args.uid == 'A2' && args.data.description == 'Raw reads' })
    }

    def 'ignores an empty annotation'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = annotatingManager(mockInstance)

        when:
        manager.registerAnnotation(remotePath('s3://bucket/work/output.json'), ArtifactAnnotation.fromMap([:]))

        then:
        readKeys('pendingAnnotations').isEmpty()
    }

    def 'ignores an annotation when no instance is configured'() {
        given:
        def manager = LaminRunManager.instance

        when:
        manager.registerAnnotation(remotePath('s3://bucket/work/output.json'),
            ArtifactAnnotation.fromMap([kind: 'dataset']))

        then:
        readKeys('pendingAnnotations').isEmpty()
    }

    def 'does not apply annotations in dry-run mode'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = LaminRunManager.instance
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', new LaminConfig([instance: 'org/inst', api_key: 'key', dry_run: true]))

        when:
        manager.registerAnnotation(remotePath('s3://bucket/work/output.json'),
            ArtifactAnnotation.fromMap([kind: 'dataset']))

        then:
        readKeys('pendingAnnotations').isEmpty()
        0 * mockInstance.updateRecord(_)
    }

    def 'reports annotations that never matched an artifact'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = annotatingManager(mockInstance)
        def published = remotePath('s3://bucket/work/published.json')
        def target = remotePath('s3://bucket/results/published.json')
        def neverPublished = remotePath('s3://bucket/work/dropped.json')

        mockInstance.getArtifactByPath(_) >> null
        mockInstance.createArtifact(_) >> [uid: 'A1', id: 11, run: 1]

        when:
        manager.registerAnnotation(published, ArtifactAnnotation.fromMap([kind: 'dataset']))
        manager.registerAnnotation(neverPublished, ArtifactAnnotation.fromMap([kind: 'dataset']))
        manager.createOutputArtifactOnFilePublish(published, target, null)
        manager.warnUnmatchedAnnotations()

        then:
        readKeys('matchedAnnotationKeys') == ['s3://bucket/work/published.json'] as Set
        readKeys('pendingAnnotations').contains('s3://bucket/work/dropped.json')
    }

    // ========== artifact stats ==========

    private static final ArtifactStats STATS = new ArtifactStats(3L, 'kAFQmDzST7DWlj99KOF_cg', 'md5', null)
    private static final String STATS_REJECTED = '{"detail":"Only key, description, kind, run can be passed, you passed: {\'_stats\': {\'size\': 3}}"}'

    private LaminRunManager statsManager(Instance mockInstance, ArtifactStatsResolver resolver, Map extraConfig = [:]) {
        def manager = LaminRunManager.instance
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', new LaminConfig([instance: 'org/inst', api_key: 'key'] + extraConfig))
        injectField(manager, 'statsResolver', resolver)
        mockInstance.getOwner() >> 'org'
        mockInstance.getName() >> 'inst'
        return manager
    }

    def 'fetchOrCreateArtifact passes client-side stats to createArtifact'() {
        given:
        def mockInstance = Mock(Instance)
        def resolver = Mock(ArtifactStatsResolver)
        def manager = statsManager(mockInstance, resolver)
        def path = remotePath('s3://bucket/out.txt')

        when:
        def result = manager.fetchOrCreateArtifact([path: path, run_id: 1, direction: 'output'])

        then:
        1 * resolver.resolve(path, false) >> STATS
        1 * mockInstance.createArtifact({ Map args ->
            args._stats == [size: 3L, hash: 'kAFQmDzST7DWlj99KOF_cg', hash_type: 'md5', n_files: null] &&
            args.skip_check_exists == true &&
            args.path == 's3://bucket/out.txt' &&
            !args.containsKey('direction')
        }) >> [uid: 'A1', id: 11, run: 1, hash: 'kAFQmDzST7DWlj99KOF_cg']
        result.uid == 'A1'
    }

    def 'fetchOrCreateArtifact caches stats for inputs only'() {
        given:
        def mockInstance = Mock(Instance)
        def resolver = Mock(ArtifactStatsResolver)
        def manager = statsManager(mockInstance, resolver)
        def path = remotePath('s3://bucket/in.txt')

        when:
        manager.fetchOrCreateArtifact([path: path, direction: 'input'])
        manager.fetchOrCreateArtifact([path: path, run_id: 1, direction: 'output'])
        manager.fetchOrCreateArtifact([path: path, run_id: 1])

        then:
        1 * resolver.resolve(path, true) >> STATS
        2 * resolver.resolve(path, false) >> STATS
        3 * mockInstance.createArtifact(_) >> [uid: 'A1', id: 11, run: 1]
    }

    def 'fetchOrCreateArtifact sends no stats when the resolver has none'() {
        given:
        def mockInstance = Mock(Instance)
        def resolver = Mock(ArtifactStatsResolver)
        def manager = statsManager(mockInstance, resolver)
        resolver.resolve(_, _) >> resolved

        when:
        def result = manager.fetchOrCreateArtifact([path: remotePath('s3://bucket/out.txt'), run_id: 1, direction: 'output'])

        then:
        1 * mockInstance.createArtifact({ Map args -> !args.containsKey('_stats') && !args.containsKey('skip_check_exists') }) >> [uid: 'A1', id: 11, run: 1]
        result.uid == 'A1'

        where:
        resolved << [null, new ArtifactStats(0L, null, null, 0L)]
    }

    def 'fetchOrCreateArtifact skips stats when artifact hashing is disabled'() {
        given:
        def mockInstance = Mock(Instance)
        def resolver = Mock(ArtifactStatsResolver)
        def manager = statsManager(mockInstance, resolver, [features: [enable_artifact_hashing: false]])

        when:
        manager.fetchOrCreateArtifact([path: remotePath('s3://bucket/in.txt'), direction: 'input'])
        manager.fetchOrCreateArtifact([path: remotePath('s3://bucket/out.txt'), run_id: 1, direction: 'output'])
        manager.fetchOrCreateArtifact([path: remotePath('s3://bucket/other.txt'), run_id: 1])

        then:
        0 * resolver.resolve(*_)
        3 * mockInstance.createArtifact({ Map args -> !args.containsKey('_stats') }) >> [uid: 'A1', id: 11, run: 1]
    }

    def 'fetchOrCreateArtifact retries without stats when the server rejects them'() {
        given:
        def mockInstance = Mock(Instance)
        def resolver = Mock(ArtifactStatsResolver)
        def manager = statsManager(mockInstance, resolver)

        when:
        def first = manager.fetchOrCreateArtifact([path: remotePath('s3://bucket/a.txt'), run_id: 1, direction: 'output'])
        def second = manager.fetchOrCreateArtifact([path: remotePath('s3://bucket/b.txt'), run_id: 1, direction: 'output'])

        then: 'the first path is retried without stats'
        1 * resolver.resolve(_, _) >> STATS
        1 * mockInstance.createArtifact({ Map a -> a.path == 's3://bucket/a.txt' && a.containsKey('_stats') }) >> {
            throw new ApiException(400, 'Bad Request', null, STATS_REJECTED)
        }
        1 * mockInstance.createArtifact({ Map a -> a.path == 's3://bucket/a.txt' && !a.containsKey('_stats') && !a.containsKey('skip_check_exists') }) >> [uid: 'A1', id: 11, run: 1]

        and: 'later paths skip stats for the rest of the run'
        1 * mockInstance.createArtifact({ Map a -> a.path == 's3://bucket/b.txt' && !a.containsKey('_stats') }) >> [uid: 'A2', id: 12, run: 1]
        first.uid == 'A1'
        second.uid == 'A2'
    }

    def 'fetchOrCreateArtifact does not retry other bad requests'() {
        given:
        def mockInstance = Mock(Instance)
        def resolver = Mock(ArtifactStatsResolver)
        def manager = statsManager(mockInstance, resolver)

        when:
        def first = manager.fetchOrCreateArtifact([path: remotePath('s3://bucket/a.txt'), run_id: 1, direction: 'output'])
        def second = manager.fetchOrCreateArtifact([path: remotePath('s3://bucket/b.txt'), run_id: 1, direction: 'output'])

        then:
        2 * resolver.resolve(_, _) >> STATS
        1 * mockInstance.createArtifact({ Map a -> a.path == 's3://bucket/a.txt' }) >> {
            throw new ApiException(400, 'Bad Request', null, '{"detail":"ValidationError: key must not be empty"}')
        }
        1 * mockInstance.createArtifact({ Map a -> a.path == 's3://bucket/b.txt' && a.containsKey('_stats') }) >> [uid: 'A2', id: 12, run: 1]
        first == null
        second.uid == 'A2'
    }

    def 'fetchOrCreateArtifact does not retry server errors'() {
        given:
        def mockInstance = Mock(Instance)
        def resolver = Mock(ArtifactStatsResolver)
        def manager = statsManager(mockInstance, resolver)
        resolver.resolve(_, _) >> STATS

        when:
        def result = manager.fetchOrCreateArtifact([path: remotePath('s3://bucket/a.txt'), run_id: 1, direction: 'output'])

        then:
        1 * mockInstance.createArtifact(_) >> { throw new ApiException(500, 'Internal Server Error', null, 'boom') }
        result == null
    }

    def 'fetchOrCreateArtifact rejects an unknown direction'() {
        given:
        def manager = statsManager(Mock(Instance), Mock(ArtifactStatsResolver))

        when:
        manager.fetchOrCreateArtifact([path: remotePath('s3://bucket/a.txt'), direction: 'sideways'])

        then:
        thrown(IllegalArgumentException)
    }

    def 'createInputArtifact asks for cached stats'() {
        given:
        def mockInstance = Mock(Instance)
        def resolver = Mock(ArtifactStatsResolver)
        def manager = statsManager(mockInstance, resolver)
        injectField(manager, 'run', [uid: 'R1', id: 1])

        when:
        def result = manager.createInputArtifact(remotePath('s3://bucket/in.txt'))

        then:
        1 * resolver.resolve(_, true) >> STATS
        1 * mockInstance.createArtifact({ Map args -> args._stats != null && args.run_id == null }) >> [uid: 'A1', id: 11]
        result.uid == 'A1'
    }

    /** Records the resolver calls the manager makes, in order. */
    static class RecordingResolver extends ArtifactStatsResolver {
        final List<String> events = []

        @Override
        ArtifactStats resolve(Path path, boolean cache) {
            events << 'resolve'
            return null
        }

        @Override
        void close() {
            events << 'close'
        }
    }

    def 'awaitArtifactTasks keeps the stats resolver for the report artifact'() {
        given:
        def resolver = new RecordingResolver()
        def manager = statsManager(Mock(Instance), resolver)

        when:
        manager.awaitArtifactTasks()

        then:
        resolver.events == []
    }

    def 'finalizeRun closes the stats resolver after updating the run'() {
        given:
        def mockInstance = Mock(Instance)
        def resolver = new RecordingResolver()
        def manager = statsManager(mockInstance, resolver)
        injectField(manager, 'run', [uid: 'R456', id: 99] as Map<String, Object>)
        def metadata = Stub(WorkflowMetadata) {
            getStart() >> OffsetDateTime.now()
            getComplete() >> OffsetDateTime.now()
        }
        injectField(manager, 'session', Stub(Session) {
            getWorkflowMetadata() >> metadata
            isSuccess() >> true
            getConfig() >> [:]
        })
        mockInstance.updateRecord(_) >> { resolver.events << 'updateRecord'; [uid: 'R456'] }

        when:
        manager.finalizeRun()

        then:
        resolver.events == ['updateRecord', 'close']
    }

    def 'finalizeRun still writes the run status when the report artifact fails'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = statsManager(mockInstance, Stub(ArtifactStatsResolver))
        injectField(manager, 'run', [uid: 'R456', id: 99] as Map<String, Object>)
        def metadata = Stub(WorkflowMetadata) {
            getStart() >> OffsetDateTime.now()
            getComplete() >> OffsetDateTime.now()
        }
        injectField(manager, 'session', Stub(Session) {
            getWorkflowMetadata() >> metadata
            isSuccess() >> true
            getConfig() >> { throw new RuntimeException('bad report config') }
        })

        when:
        manager.finalizeRun()

        then:
        1 * mockInstance.updateRecord({ Map args -> args.data._status_code == RunStatus.COMPLETED.code }) >> [uid: 'R456']
        manager.trackingFailures == 1
    }

    private LaminRunManager finishedManager(Instance mockInstance, Closure config) {
        def manager = statsManager(mockInstance, Stub(ArtifactStatsResolver))
        injectField(manager, 'run', [uid: 'R456', id: 99] as Map<String, Object>)
        def metadata = Stub(WorkflowMetadata) {
            getStart() >> OffsetDateTime.now()
            getComplete() >> OffsetDateTime.now()
        }
        injectField(manager, 'session', Stub(Session) {
            getWorkflowMetadata() >> metadata
            isSuccess() >> true
            getConfig() >> { config.call() }
        })
        return manager
    }

    def 'finalizeRun writes the run status before registering the report'() {
        given:
        def events = []
        def mockInstance = Mock(Instance)
        def manager = finishedManager(mockInstance) { events << 'report'; [:] }
        mockInstance.updateRecord(_) >> { events << 'status'; [uid: 'R456'] }

        when:
        manager.finalizeRun()

        then:
        events.first() == 'status'
        events.contains('report')
    }

    def 'finalizeRun links a registered report in a separate update'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = finishedManager(mockInstance) { [report: [enabled: true, file: 'https://example.com/report.html']] }

        when:
        manager.finalizeRun()

        then:
        1 * mockInstance.updateRecord({ Map args ->
            args.data._status_code == RunStatus.COMPLETED.code && !args.data.containsKey('report_id')
        }) >> [uid: 'R456']

        then:
        1 * mockInstance.createArtifact(_) >> [uid: 'A9', id: 9]

        then:
        1 * mockInstance.updateRecord({ Map args -> args.data == [report_id: 9] }) >> [uid: 'R456']
    }

    def 'counts a report that could not be linked to the run'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = finishedManager(mockInstance) { [report: [enabled: true, file: 'https://example.com/report.html']] }
        mockInstance.createArtifact(_) >> [uid: 'A9', id: 9]
        mockInstance.updateRecord(_) >> { Map args ->
            if (args.data.containsKey('report_id')) {
                throw new RuntimeException('hub down')
            }
            [uid: 'R456']
        }

        when:
        manager.finalizeRun()

        then:
        noExceptionThrown()
        manager.trackingFailures == 1
    }

    def 'finalizeRun retries the status when the early write of an aborted run failed'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = finishedManager(mockInstance) { [:] }
        def metadata = Stub(WorkflowMetadata) {
            getStart() >> OffsetDateTime.now()
            getComplete() >> OffsetDateTime.now()
        }
        injectField(manager, 'session', Stub(Session) {
            getWorkflowMetadata() >> metadata
            isAborted() >> true
            getError() >> new RuntimeException('task failed')
            getConfig() >> [:]
        })
        int attempts = 0
        mockInstance.updateRecord(_) >> {
            if (++attempts == 1) {
                throw new RuntimeException('hub down')
            }
            [uid: 'R456']
        }

        when:
        try {
            manager.writeRunStatusIfAborted()
        } catch (RuntimeException ignored) {
        }
        manager.finalizeRun()

        then:
        attempts == 2
    }

    def 'writeRunStatusIfAborted leaves a run that is still going alone'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = finishedManager(mockInstance) { [:] }

        when:
        manager.writeRunStatusIfAborted()

        then:
        0 * mockInstance.updateRecord(_)
    }

    private LaminRunManager trackingManager(Instance mockInstance) {
        def manager = LaminRunManager.instance
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', new LaminConfig([instance: 'org/inst', api_key: 'key']))
        injectField(manager, 'run', [uid: 'R1', id: 1])
        mockInstance.getOwner() >> 'org'
        mockInstance.getName() >> 'inst'
        return manager
    }

    private Path s3MockPath(String uri = 's3://bucket/file.txt') {
        def path = Mock(Path)
        path.toUri() >> new URI(uri)
        return path
    }

    def 'counts no tracking failures when everything is registered'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = trackingManager(mockInstance)
        mockInstance.createArtifact(_) >> [uid: 'A1', id: 11, run: 1]

        when:
        manager.createInputArtifactsAsync('task', [s3MockPath()])
        manager.awaitArtifactTasks()

        then:
        manager.trackingFailures == 0
    }

    def 'counts an artifact that could not be created'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = trackingManager(mockInstance)
        mockInstance.createArtifact(_) >> { throw new RuntimeException('hub down') }

        when:
        manager.createInputArtifactsAsync('task', [s3MockPath()])
        manager.awaitArtifactTasks()

        then:
        manager.trackingFailures == 1
    }

    def 'counts an Error thrown inside an artifact task'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = trackingManager(mockInstance)
        mockInstance.createArtifact(_) >> { throw new AssertionError('unexpected') }

        when:
        manager.createInputArtifactsAsync('task', [s3MockPath()])
        manager.awaitArtifactTasks()

        then:
        manager.trackingFailures == 1
    }

    def 'counts an input link that could not be written'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = trackingManager(mockInstance)
        mockInstance.createArtifact(_) >> [uid: 'A1', id: 11, run: 7]
        mockInstance.upsertRecord(_) >> { throw new RuntimeException('hub down') }

        when:
        manager.createInputArtifactsAsync('task', [s3MockPath()])
        manager.awaitArtifactTasks()

        then:
        manager.trackingFailures == 1
    }

    def 'counts work that arrives after the artifact executor shut down'() {
        given:
        def manager = trackingManager(Mock(Instance))
        manager.awaitArtifactTasks()

        when:
        manager.createInputArtifactsAsync('task', [s3MockPath()])

        then:
        manager.trackingFailures == 1
    }

    def 'counts a configured path closure that fails, without throwing'() {
        given:
        def manager = trackingManager(Mock(Instance))
        injectField(manager, 'config', new LaminConfig([
            instance: 'org/inst', api_key: 'key', input_artifacts: [include_paths: { throw new RuntimeException('boom') }]
        ]))
        injectField(manager, 'session', Stub(Session))

        when:
        manager.processConfigPathsAsync('input')

        then:
        noExceptionThrown()
        manager.trackingFailures == 1
    }

    def 'reset clears the tracking failures'() {
        given:
        def manager = trackingManager(Mock(Instance))
        manager.awaitArtifactTasks()
        manager.createInputArtifactsAsync('task', [s3MockPath()])

        when:
        manager.reset()

        then:
        manager.trackingFailures == 0
    }

    def 'awaitArtifactTasks gives up after its timeout and counts the work it abandoned'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = trackingManager(mockInstance)
        injectField(manager, 'artifactExecutor', Executors.newFixedThreadPool(1))
        def release = new CountDownLatch(1)
        mockInstance.createArtifact(_) >> {
            release.await(30, TimeUnit.SECONDS)
            [uid: 'A1', id: 11, run: 1]
        }
        manager.createInputArtifactsAsync('blocked', [s3MockPath()])
        manager.createInputArtifactsAsync('queued', [s3MockPath('s3://bucket/other.txt')])

        when:
        long before = System.nanoTime()
        manager.awaitArtifactTasks({ 1L } as LongSupplier)
        long elapsedSeconds = (System.nanoTime() - before) / 1_000_000_000L

        then:
        elapsedSeconds < 10
        manager.trackingFailures >= 1

        cleanup:
        release.countDown()
    }

    def 'awaitArtifactTasks stops early when its limit drops during the wait'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = trackingManager(mockInstance)
        def release = new CountDownLatch(1)
        mockInstance.createArtifact(_) >> {
            release.await(30, TimeUnit.SECONDS)
            [uid: 'A1', id: 11, run: 1]
        }
        manager.createInputArtifactsAsync('blocked', [s3MockPath()])
        def limit = new AtomicLong(3600)

        when: 'a cancel arrives while the wait is already under way'
        Thread.start {
            sleep(1500)
            limit.set(0)
        }
        long before = System.nanoTime()
        manager.awaitArtifactTasks({ limit.get() } as LongSupplier)
        long elapsedSeconds = (System.nanoTime() - before) / 1_000_000_000L

        then:
        elapsedSeconds < 20

        cleanup:
        release.countDown()
    }

    def 'waits for artifact tasks depending on how the session ended'() {
        given:
        injectField(LaminRunManager.instance, 'session', Stub(Session) {
            getError() >> error
        })

        expect:
        LaminRunManager.instance.artifactDrainTimeoutSeconds() == expected

        where:
        scenario           | error                                         || expected
        'normal end'       | null                                          || 3600
        'cancel by signal' | new AbortSignalException(new Signal('TERM'))  || 20
        'task failure'     | new RuntimeException('task failed')           || 3600
    }

    def 'waits the normal time for artifact tasks when there is no session'() {
        expect:
        LaminRunManager.instance.artifactDrainTimeoutSeconds() == 3600
    }
}
