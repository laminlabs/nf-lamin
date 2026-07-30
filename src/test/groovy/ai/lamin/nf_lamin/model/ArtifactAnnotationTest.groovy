package ai.lamin.nf_lamin.model

import spock.lang.Specification

class ArtifactAnnotationTest extends Specification {

    def 'parses all accepted options'() {
        when:
        def annotation = ArtifactAnnotation.fromMap([
            kind: 'dataset',
            description: 'Aligned reads',
            ulabel_uids: ['abc123', '+qc-passed'],
            project_uids: ['proj123']
        ])

        then:
        annotation.kind == 'dataset'
        annotation.description == 'Aligned reads'
        annotation.ulabelUids == ['abc123', '+qc-passed']
        annotation.projectUids == ['proj123']
        !annotation.isEmpty()
    }

    def 'treats a null or empty map as an empty annotation'() {
        expect:
        ArtifactAnnotation.fromMap(opts).isEmpty()

        where:
        opts << [null, [:]]
    }

    def 'accepts a bare string for uid options'() {
        when:
        def annotation = ArtifactAnnotation.fromMap([ulabel_uids: '+qc-passed', project_uids: 'proj123'])

        then:
        annotation.ulabelUids == ['+qc-passed']
        annotation.projectUids == ['proj123']
    }

    def 'drops null and blank uid entries'() {
        when:
        def annotation = ArtifactAnnotation.fromMap([ulabel_uids: ['abc', null, '  ', ' def ']])

        then:
        annotation.ulabelUids == ['abc', 'def']
    }

    def 'renders a GString description as a string'() {
        given:
        def sample = 'S001'

        when:
        def annotation = ArtifactAnnotation.fromMap([description: "Reads for ${sample}"])

        then:
        annotation.description instanceof String
        annotation.description == 'Reads for S001'
    }

    def 'rejects unknown options'() {
        when:
        ArtifactAnnotation.fromMap([kind: 'dataset', kinds: 'dataset'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('kinds')
        e.message.contains('Accepted options')
    }

    def 'rejects features with a pointer to the tracking issue'() {
        when:
        ArtifactAnnotation.fromMap([features: [sample_id: 'S001']])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('not supported yet')
        e.message.contains('nf-lamin/issues/102')
    }

    def 'rejects an invalid kind'() {
        when:
        ArtifactAnnotation.fromMap([kind: 'report'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("invalid kind 'report'")
    }

    def 'accepts every kind LaminDB knows'() {
        expect:
        ArtifactAnnotation.fromMap([kind: kind]).kind == kind

        where:
        kind << ArtifactAnnotation.VALID_KINDS
    }

    def 'is empty when only unset options are given'() {
        expect:
        ArtifactAnnotation.fromMap([kind: null, ulabel_uids: []]).isEmpty()
    }
}
