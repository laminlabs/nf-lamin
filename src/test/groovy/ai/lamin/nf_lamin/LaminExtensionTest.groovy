package ai.lamin.nf_lamin

import spock.lang.Specification
import ai.lamin.nf_lamin.instance.Instance
import ai.lamin.nf_lamin.hub.InstanceSettings
import java.nio.file.Path

class LaminExtensionTest extends Specification {

    LaminExtension extension

    def setup() {
        LaminRunManager.instance.reset()
        extension = new LaminExtension()
    }

    // ========== getRunUid tests ==========

    def 'getRunUid returns null when no run is set'() {
        expect:
        extension.getRunUid() == null
    }

    def 'getRunUid returns uid when run is set'() {
        given:
        LaminRunManager.instance.updateRun([uid: 'run123456789012', id: 42])

        expect:
        extension.getRunUid() == 'run123456789012'
    }

    def 'getRunUid returns null when run has no uid field'() {
        given:
        LaminRunManager.instance.updateRun([id: 42])

        expect:
        extension.getRunUid() == null
    }

    // ========== getTransformUid tests ==========

    def 'getTransformUid returns null when no transform is set'() {
        expect:
        extension.getTransformUid() == null
    }

    def 'getTransformUid returns uid when transform is set'() {
        given:
        LaminRunManager.instance.updateTransform([uid: 'transform1234567', id: 99])

        expect:
        extension.getTransformUid() == 'transform1234567'
    }

    def 'getTransformUid returns null when transform has no uid field'() {
        given:
        LaminRunManager.instance.updateTransform([id: 99])

        expect:
        extension.getTransformUid() == null
    }

    // ========== getInstanceSlug tests ==========

    def 'getInstanceSlug returns null when no instance is set'() {
        expect:
        extension.getInstanceSlug() == null
    }

    def 'getInstanceSlug returns slug when instance is set'() {
        given:
        def settings = new InstanceSettings([
            id: UUID.randomUUID().toString(),
            owner: 'laminlabs',
            name: 'lamindata',
            schema_id: UUID.randomUUID().toString(),
            api_url: 'https://api.example.com'
        ])
        def mockInstance = Mock(Instance) {
            getSettings() >> settings
        }
        LaminRunManager.instance.setCurrentInstance(mockInstance)

        expect:
        extension.getInstanceSlug() == 'laminlabs/lamindata'
    }

    def 'getInstanceSlug returns correct format for different owners and names'() {
        given:
        def settings = new InstanceSettings([
            id: UUID.randomUUID().toString(),
            owner: 'my-org',
            name: 'production-db',
            schema_id: UUID.randomUUID().toString(),
            api_url: 'https://api.example.com'
        ])
        def mockInstance = Mock(Instance) {
            getSettings() >> settings
        }
        LaminRunManager.instance.setCurrentInstance(mockInstance)

        expect:
        extension.getInstanceSlug() == 'my-org/production-db'
    }

    // ========== annotateArtifact tests ==========

    def 'annotateArtifact returns its target unchanged'() {
        given:
        Path path = Path.of('/tmp/output.json')

        expect:
        extension.annotateArtifact([kind: 'dataset'], path).is(path)
    }

    def 'annotateArtifact is a no-op when no instance is configured'() {
        given:
        Path path = Path.of('/tmp/output.json')

        when:
        extension.annotateArtifact([kind: 'dataset', ulabel_uids: ['+qc']], path)

        then:
        noExceptionThrown()
    }

    def 'annotateArtifact registers the annotation for a path'() {
        given:
        def mockInstance = Mock(Instance)
        LaminRunManager.instance.setCurrentInstance(mockInstance)
        Path path = Path.of('/tmp/output.json')

        when:
        extension.annotateArtifact([kind: 'dataset'], path)

        then:
        pendingAnnotationKeys().contains(path.toUri().toString())
    }

    def 'annotateArtifact accepts a path given as a string'() {
        given:
        def mockInstance = Mock(Instance)
        LaminRunManager.instance.setCurrentInstance(mockInstance)

        when:
        def result = extension.annotateArtifact([kind: 'dataset'], '/tmp/output.json')

        then:
        result == '/tmp/output.json'
        pendingAnnotationKeys().contains(Path.of('/tmp/output.json').toUri().toString())
    }

    def 'annotateArtifact annotates every path of a collection'() {
        given:
        def mockInstance = Mock(Instance)
        LaminRunManager.instance.setCurrentInstance(mockInstance)
        def paths = [Path.of('/tmp/a.json'), Path.of('/tmp/b.json')]

        when:
        def result = extension.annotateArtifact([kind: 'dataset'], paths)

        then:
        result.is(paths)
        pendingAnnotationKeys().containsAll(paths.collect { it.toUri().toString() })
    }

    def 'annotateArtifact works without named arguments'() {
        given:
        Path path = Path.of('/tmp/output.json')

        expect:
        extension.annotateArtifact(path).is(path)
    }

    def 'annotateArtifact rejects a target that is not a file'() {
        when:
        extension.annotateArtifact([kind: 'dataset'], 42)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('annotateArtifact')
        e.message.contains('cannot resolve a Integer to a file')
    }

    def 'annotateArtifact rejects unknown options'() {
        when:
        extension.annotateArtifact([kynd: 'dataset'], Path.of('/tmp/output.json'))

        then:
        thrown(IllegalArgumentException)
    }

    private static Set<String> pendingAnnotationKeys() {
        def field = LaminRunManager.getDeclaredField('pendingAnnotations')
        field.accessible = true
        return (field.get(LaminRunManager.instance) as Map).keySet()
    }

    // ========== Integration-style tests ==========

    def 'all extension functions work together'() {
        given:
        def settings = new InstanceSettings([
            id: UUID.randomUUID().toString(),
            owner: 'testorg',
            name: 'testinstance',
            schema_id: UUID.randomUUID().toString(),
            api_url: 'https://api.example.com'
        ])
        def mockInstance = Mock(Instance) {
            getSettings() >> settings
        }

        LaminRunManager.instance.updateTransform([uid: 'T123456789012345', id: 1])
        LaminRunManager.instance.updateRun([uid: 'R123456789012345678', id: 2])
        LaminRunManager.instance.setCurrentInstance(mockInstance)

        expect:
        extension.getTransformUid() == 'T123456789012345'
        extension.getRunUid() == 'R123456789012345678'
        extension.getInstanceSlug() == 'testorg/testinstance'
    }

    def 'reset clears all state accessible via extension'() {
        given:
        def settings = new InstanceSettings([
            id: UUID.randomUUID().toString(),
            owner: 'testorg',
            name: 'testinstance',
            schema_id: UUID.randomUUID().toString(),
            api_url: 'https://api.example.com'
        ])
        def mockInstance = Mock(Instance) {
            getSettings() >> settings
        }

        LaminRunManager.instance.updateTransform([uid: 'T999'])
        LaminRunManager.instance.updateRun([uid: 'R999'])
        LaminRunManager.instance.setCurrentInstance(mockInstance)

        when:
        LaminRunManager.instance.reset()

        then:
        extension.getTransformUid() == null
        extension.getRunUid() == null
        extension.getInstanceSlug() == null
    }
}
