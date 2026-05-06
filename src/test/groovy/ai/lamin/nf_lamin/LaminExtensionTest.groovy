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
