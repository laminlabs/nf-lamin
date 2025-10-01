package ai.lamin.nf_lamin

import spock.lang.Specification

class LaminRunManagerTest extends Specification {

    def setup() {
        LaminRunManager.instance.reset()
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
        extension.laminTransformUid() == 'T123'
        extension.laminRunUid() == 'R456'
        extension.laminTransformMetadata().id == 42
        extension.laminRunMetadata().id == 99
    }

    def 'reset clears stored state'() {
        given:
        LaminRunManager.instance.updateRun([uid: 'R999'])

        when:
        LaminRunManager.instance.reset()

        then:
        LaminRunManager.instance.run == null
        new LaminExtension().laminRunUid() == null
    }
}
