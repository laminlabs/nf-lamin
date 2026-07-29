package ai.lamin.nf_lamin.util

import nextflow.Session
import nextflow.script.WorkflowMetadata
import spock.lang.Requires
import spock.lang.Specification

class SeqeraPlatformHelperTest extends Specification {

    /** Mimics nextflow.script.PlatformMetadata (Nextflow >= 26.04). */
    static class FakePlatform {

        Object workflowId
        Object workflowUrl

    }

    /** Mimics nextflow.script.WorkflowMetadata with a `platform` property. */
    static class FakeMetadata {

        Object platform

    }

    private static FakeMetadata metadataWith(Object url, Object id) {
        return new FakeMetadata(platform: new FakePlatform(workflowUrl: url, workflowId: id))
    }

    def 'reads the watch URL'() {
        given:
        def metadata = metadataWith('https://cloud.seqera.io/orgs/o/workspaces/w/watch/abc', 'abc')

        expect:
        SeqeraPlatformHelper.readReference(metadata) == 'https://cloud.seqera.io/orgs/o/workspaces/w/watch/abc'
    }

    def 'returns null when the watch URL is #scenario, even with a workflow id'() {
        expect:
        // the workflow id on its own is not enough to reconstruct the URL, so it is not stored
        SeqeraPlatformHelper.readReference(metadataWith(url, 'b0siCig3qoUvZ')) == null

        where:
        scenario      | url
        'null'        | null
        'empty'       | ''
        'whitespace'  | '   '
    }

    def 'returns null when the platform metadata is empty'() {
        expect:
        // Nextflow >= 26.04 without Seqera Platform: getPlatform() lazily returns an empty object
        SeqeraPlatformHelper.readReference(metadataWith(null, null)) == null
    }

    def 'returns null when there is no platform metadata at all'() {
        expect:
        SeqeraPlatformHelper.readReference(new FakeMetadata(platform: null)) == null
    }

    def 'returns null on Nextflow versions without workflow.platform'() {
        expect:
        // Nextflow 25.10: WorkflowMetadata has no getPlatform() method
        SeqeraPlatformHelper.readReference(Stub(WorkflowMetadata)) == null
        SeqeraPlatformHelper.readReference(new Object()) == null
        SeqeraPlatformHelper.readReference(null) == null
    }

    def 'returns a plain String, never a GString'() {
        given:
        String id = 'b0siCig3qoUvZ'
        def metadata = metadataWith("https://cloud.seqera.io/watch/${id}", null)

        when:
        Object reference = SeqeraPlatformHelper.readReference(metadata)

        then:
        // the Lamin API rejects GStrings
        reference.getClass() == String
    }

    def 'resolveRunReference tolerates a missing session or metadata'() {
        given:
        def session = Stub(Session) {
            getWorkflowMetadata() >> null
        }

        expect:
        SeqeraPlatformHelper.resolveRunReference(null) == null
        SeqeraPlatformHelper.resolveRunReference(session) == null
    }

    def 'reference type is Seqera'() {
        expect:
        // matches the reference_type of the existing Seqera runs on laminlabs/lamindata
        SeqeraPlatformHelper.REFERENCE_TYPE == 'Seqera'
    }

    /**
     * Guards the getter names against the real Nextflow classes. The fakes above cannot catch a
     * typo in 'getPlatform' / 'getWorkflowUrl' / 'getWorkflowId', because a wrong name and a
     * genuinely absent one both resolve to null. Skipped while the plugin compiles against
     * Nextflow 25.10; runs as soon as the floor is raised to 26.04.
     */
    @Requires({ SeqeraPlatformHelperTest.platformMetadataClass() != null })
    def 'reads the real Nextflow PlatformMetadata'() {
        given:
        def platform = platformMetadataClass().getConstructor().newInstance()
        platform.workflowId = 'b0siCig3qoUvZ'
        platform.workflowUrl = 'https://cloud.seqera.io/orgs/o/workspaces/w/watch/b0siCig3qoUvZ'

        expect:
        WorkflowMetadata.getMethod('getPlatform') != null

        and:
        SeqeraPlatformHelper.readReference(new FakeMetadata(platform: platform)) ==
            'https://cloud.seqera.io/orgs/o/workspaces/w/watch/b0siCig3qoUvZ'

        when:
        platform.workflowUrl = null

        then:
        SeqeraPlatformHelper.readReference(new FakeMetadata(platform: platform)) == null
    }

    /** @return the Nextflow >= 26.04 PlatformMetadata class, or null on older versions */
    static Class platformMetadataClass() {
        try {
            return Class.forName('nextflow.script.PlatformMetadata')
        }
        catch (ClassNotFoundException e) {
            return null
        }
    }
}
