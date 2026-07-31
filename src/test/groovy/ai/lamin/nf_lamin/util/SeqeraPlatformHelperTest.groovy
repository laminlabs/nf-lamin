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
        // the workflow id alone is not enough to reconstruct the URL
        SeqeraPlatformHelper.readReference(metadataWith(url, 'b0siCig3qoUvZ')) == null

        where:
        scenario      | url
        'null'        | null
        'empty'       | ''
        'whitespace'  | '   '
    }

    def 'returns null when the platform metadata is empty'() {
        expect:
        // without Seqera Platform, getPlatform() returns an empty object
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

    // ========== observer fallback (Nextflow < 26.04) ==========

    /** Mimics the nf-tower observer, which keeps the watch URL in a private field. */
    static class TowerClient {

        private String watchUrl

        TowerClient(String watchUrl) {
            this.watchUrl = watchUrl
        }

    }

    /** Mimics a session, which keeps its observers in a private field. */
    static class FakeSession {

        private List observersV2

        FakeSession(List observersV2) {
            this.observersV2 = observersV2
        }

    }

    def 'reads the watch URL from the nf-tower observer'() {
        given:
        def session = new FakeSession([new Object(), new TowerClient('https://cloud.seqera.io/watch/abc')])

        expect:
        SeqeraPlatformHelper.readReferenceFromObservers(session) ==
            'https://cloud.seqera.io/watch/abc'
    }

    def 'returns null when the watch URL on the observer is #scenario'() {
        expect:
        SeqeraPlatformHelper.readReferenceFromObservers(new FakeSession([new TowerClient(url)])) == null

        where:
        scenario     | url
        'null'       | null
        'empty'      | ''
        'whitespace' | '   '
    }

    def 'returns null when nothing recognisable is there'() {
        expect:
        SeqeraPlatformHelper.readReferenceFromObservers(null) == null
        // no nf-tower observer
        SeqeraPlatformHelper.readReferenceFromObservers(new FakeSession([new Object()])) == null
        // no observers at all
        SeqeraPlatformHelper.readReferenceFromObservers(new FakeSession(null)) == null
        // not a session
        SeqeraPlatformHelper.readReferenceFromObservers(new Object()) == null
    }

    /**
     * Guards the field name against the real Nextflow class -- the fake above cannot catch a
     * typo, because a wrong name and an absent one both resolve to null.
     */
    def 'the observer field it looks for exists on the real Session'() {
        expect:
        Session.getDeclaredField('observersV2') != null
    }

    /**
     * Guards the getter names against the real Nextflow classes -- the fakes above cannot catch a
     * typo, because a wrong name and an absent one both resolve to null. Skipped until the
     * Nextflow floor is raised to 26.04.
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
