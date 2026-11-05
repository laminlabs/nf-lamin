/*
 * Copyright 2025, Lamin Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.lamin.nf_lamin

import ai.lamin.lamin_api_client.ApiException
import ai.lamin.nf_lamin.helpers.LogCapture
import ai.lamin.nf_lamin.hub.InstanceSettings
import ai.lamin.nf_lamin.hub.LaminHub
import ai.lamin.nf_lamin.instance.Instance
import ai.lamin.nf_lamin.model.ArtifactAnnotation
import ai.lamin.nf_lamin.nio.LaminFileSystemProvider
import ai.lamin.nf_lamin.nio.LaminPath
import ai.lamin.nf_lamin.util.ArtifactStatsResolver
import nextflow.Session
import spock.lang.Specification

import java.lang.reflect.Field
import java.nio.file.Path

/**
 * Tracking is best effort, so a failure doesn't fail the pipeline: it shouldn't look like it did,
 * and a hub outage shouldn't print a line for every file.
 */
class ConsoleMessagesTest extends Specification {

    LogCapture logs

    def setup() {
        LaminRunManager.instance.reset()
        injectField(LaminRunManager.instance, 'statsResolver', Stub(ArtifactStatsResolver) { resolve(_, _) >> null })
        logs = new LogCapture()
    }

    def cleanup() {
        logs.stop()
        LaminRunManager.instance.reset()
    }

    private static void injectField(Object target, String fieldName, Object value) {
        Field field = target.class.getDeclaredField(fieldName)
        field.accessible = true
        field.set(target, value)
    }

    private Path remotePath(String uri) {
        def path = Stub(Path)
        path.toUri() >> new URI(uri)
        path.toAbsolutePath() >> path
        path.normalize() >> path
        return path
    }

    private LaminRunManager manager(Instance mockInstance, Map extraConfig = [:]) {
        def manager = LaminRunManager.instance
        manager.setCurrentInstance(mockInstance)
        injectField(manager, 'config', new LaminConfig([instance: 'org/inst', api_key: 'key'] + extraConfig))
        injectField(manager, 'run', [uid: 'R1', id: 1])
        injectField(manager, 'session', Stub(Session) { getConfig() >> [:] })
        return manager
    }

    private void publish(LaminRunManager manager, String... uris) {
        for (String uri : uris) {
            manager.createOutputArtifactOnFilePublish(null, remotePath(uri), null)
        }
    }

    def 'shows the first artifact that could not be created and logs the rest at debug level'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = manager(mockInstance)
        mockInstance.createArtifact(_) >> { throw new RuntimeException('hub down') }

        when:
        publish(manager, 's3://bucket/a.txt', 's3://bucket/b.txt', 's3://bucket/c.txt')

        then:
        logs.errors == []
        logs.warnings.size() == 1
        logs.warnings[0].contains('s3://bucket/a.txt')
        logs.warnings[0].contains('hub down')
        logs.debugs.any { it.contains('s3://bucket/b.txt') && it.contains('Failed to create artifact') }
        manager.trackingFailures == 3
    }

    def 'shows a storage path with the slashes a user would write'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = manager(mockInstance)
        mockInstance.createArtifact(_) >> { throw new RuntimeException('hub down') }

        when: 'the S3 provider renders its paths with three slashes'
        publish(manager, 's3:///bucket/a.txt')

        then:
        logs.warnings.size() == 1
        logs.warnings[0].contains('s3://bucket/a.txt')
    }

    def 'says that the hub did not find what was asked for instead of showing a null response'() {
        given:
        def settings = new InstanceSettings([
            id       : UUID.randomUUID().toString(),
            owner    : 'laminlabs',
            name     : 'lamindata',
            schema_id: UUID.randomUUID().toString(),
            api_url  : 'http://localhost:1',
        ])
        LaminHub hub = Stub(LaminHub)
        def instance = new Instance(hub, settings, 0, 1, 1) {
            @Override
            protected Object callApi(String opName, String opArgs, Closure closure) {
                return null
            }
        }

        when:
        instance.createArtifact([path: 's3://bucket/a.txt'])

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('404')
        !e.message.contains('null')
    }

    def 'shows the first link that could not be written and logs the rest at debug level'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = manager(mockInstance)
        mockInstance.createArtifact(_) >>> [[uid: 'A1', id: 11, run: 7], [uid: 'A2', id: 12, run: 7]]
        mockInstance.upsertRecord(_) >> { throw new RuntimeException('hub down') }

        when:
        manager.createInputArtifact(remotePath('s3://bucket/a.txt'))
        manager.createInputArtifact(remotePath('s3://bucket/b.txt'))

        then:
        logs.errors == []
        logs.warnings.size() == 1
        logs.warnings[0].contains('A1')
        logs.debugs.any { it.contains('A2') && it.contains('Could not link') }
        manager.trackingFailures == 2
    }

    def 'shows each kind of failure once'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = manager(mockInstance)
        mockInstance.createArtifact({ it.path.contains('good') }) >> [uid: 'A1', id: 11, run: 7]
        mockInstance.createArtifact({ !it.path.contains('good') }) >> { throw new RuntimeException('hub down') }
        mockInstance.upsertRecord(_) >> { throw new RuntimeException('hub down') }

        when:
        manager.createInputArtifact(remotePath('s3://bucket/good.txt'))
        publish(manager, 's3://bucket/bad.txt')

        then:
        logs.warnings.size() == 2
    }

    def 'shows a failure again after a reset'() {
        given:
        def mockInstance = Mock(Instance)
        mockInstance.createArtifact(_) >> { throw new RuntimeException('hub down') }
        publish(manager(mockInstance), 's3://bucket/a.txt')

        when:
        LaminRunManager.instance.reset()
        injectField(LaminRunManager.instance, 'statsResolver', Stub(ArtifactStatsResolver) { resolve(_, _) >> null })
        publish(manager(mockInstance), 's3://bucket/b.txt')

        then:
        logs.warnings.size() == 2
    }

    def 'shows a project that does not exist once for each name'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = manager(mockInstance, [project_uids: ['missingA1234', 'missingB1234']])
        mockInstance.createArtifact(_) >>> [[uid: 'A1', id: 11, run: 1], [uid: 'A2', id: 12, run: 1]]
        mockInstance.getRecord(_) >> null

        when:
        publish(manager, 's3://bucket/a.txt', 's3://bucket/b.txt')

        then:
        logs.warnings.findAll { it.contains('Could not find project') }.size() == 2
    }

    def 'still reports the total of failures that were not shown'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = manager(mockInstance)
        mockInstance.createArtifact(_) >> { throw new RuntimeException('hub down') }
        publish(manager, 's3://bucket/a.txt', 's3://bucket/b.txt')

        when:
        manager.warnTrackingFailures()

        then:
        logs.warnings.size() == 2
        logs.warnings[1].startsWith('2 artifact or link operation(s) failed')
    }

    def 'logs a failed connection as one warning'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = manager(mockInstance)
        mockInstance.getOwner() >> 'org'
        mockInstance.getName() >> 'inst'
        mockInstance.getAccount() >> { throw new ApiException(500, 'hub down') }

        when:
        manager.testConnection()

        then:
        logs.errors == []
        logs.warnings.size() == 1
        logs.warnings[0].contains('org/inst')
        logs.warnings[0].contains('hub down')
    }

    def 'leaves reporting a failed initialization to the observer'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = manager(mockInstance, [space_uid: 'space1234567'])
        mockInstance.getRecord(_) >> { throw new IllegalStateException('hub down') }

        when:
        manager.initializeRun()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'hub down'
        logs.errors == []
        logs.warnings == []
    }

    def 'reports a failed initialization as one warning'() {
        given:
        def session = Stub(Session) {
            getConfig() >> [lamin: [instance: 'not-a-valid-instance', api_key: 'key']]
        }

        when:
        new LaminObserver().onFlowCreate(session)

        then:
        thrown(Exception)
        logs.errors == []
        logs.warnings.size() == 1
        logs.warnings[0].contains('Could not initialize Lamin run')
    }

    def 'logs a failed space lookup as a warning'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = manager(mockInstance, [space_uid: 'space1234567'])
        mockInstance.getRecord(_) >> { throw new RuntimeException('hub down') }
        mockInstance.getRecords(_) >> { throw new RuntimeException('hub down') }

        when:
        manager.resolveSpaceAndBranch()

        then:
        logs.errors == []
        logs.warnings.size() == 1
        logs.warnings[0].contains('hub down')
    }

    def 'logs a failed transform lookup as one warning'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = manager(mockInstance, [transform_uid: 'transform123'])
        mockInstance.fetchTransform(_) >> { throw new RuntimeException('hub down') }

        when: 'the lookup that follows has no session to work with'
        try {
            manager.fetchOrCreateTransform()
        } catch (Exception ignored) {
        }

        then:
        logs.errors == []
        logs.warnings.findAll { it.contains('transform123') }.size() == 1
        logs.warnings.size() == 1
    }

    def 'logs a failed run lookup as one warning'() {
        given:
        def mockInstance = Mock(Instance)
        def manager = manager(mockInstance, [run_uid: 'run123456789'])
        injectField(manager, 'transform', [uid: 'T1', id: 2])
        mockInstance.getRecord(_) >> { throw new RuntimeException('hub down') }

        when: 'creating the new run that follows has no session to work with'
        try {
            manager.fetchOrCreateRun()
        } catch (Exception ignored) {
        }

        then:
        logs.errors == []
        logs.warnings.findAll { it.contains('run123456789') }.size() == 1
        logs.warnings.size() == 1
    }

    def 'logs a failed finalization step as a warning'() {
        when:
        LaminObserver.finalizationStep('do something', { throw new RuntimeException('hub down') } as Runnable)

        then:
        logs.errors == []
        logs.warnings == ['Could not do something: hub down']
    }

    def 'logs dry-run annotations at debug level'() {
        given:
        def manager = manager(Mock(Instance), [dry_run: true])

        when:
        manager.registerAnnotation(remotePath('s3://bucket/a.txt'), ArtifactAnnotation.fromMap([description: 'x']))

        then:
        logs.infos == []
        logs.debugs.any { it.contains('would annotate') }
    }

    private Instance realInstance(int maxRetries) {
        def settings = new InstanceSettings([
            id       : UUID.randomUUID().toString(),
            owner    : 'laminlabs',
            name     : 'lamindata',
            schema_id: UUID.randomUUID().toString(),
            api_url  : 'http://localhost:1',
        ])
        return new Instance(Stub(LaminHub), settings, maxRetries, 1, 1)
    }

    def 'logs API retries at debug level'() {
        given:
        def instance = realInstance(1)
        int attempts = 0

        when:
        def result = instance.callApi('POST createArtifact', 'data=z') {
            if (attempts++ == 0) {
                throw new ApiException(502, 'boom', null, '{"detail":"Bad Gateway"}')
            }
            return 'ok'
        }

        then:
        result == 'ok'
        logs.warnings == []
        logs.debugs.any { it.contains('Retrying') }
    }

    def 'puts the reason in the error when the access token cannot be refreshed'() {
        when:
        new LaminHub('http://localhost:1', 'anon', 'key').refreshAccessToken()

        then:
        def e = thrown(RuntimeException)
        e.message.startsWith('Failed to refresh access token: ')
        logs.errors == []
        logs.warnings == []
    }

    def 'shows that cloud credentials are missing once for each storage location'() {
        given:
        LaminHub hub = Stub(LaminHub) {
            getCloudAccess(_) >> { throw new RuntimeException('hub down') }
        }
        def provider = new LaminFileSystemProvider() {
            @Override
            protected LaminHub getHub() {
                return hub
            }
        }

        when:
        def resolve = LaminFileSystemProvider.getDeclaredMethod('tryResolveWithManagedS3Credentials', LaminPath, String, String)
        resolve.accessible = true
        resolve.invoke(provider, null, 's3://bucket-a/root', 'a.txt')
        resolve.invoke(provider, null, 's3://bucket-a/root', 'b.txt')
        resolve.invoke(provider, null, 's3://bucket-b/root', 'a.txt')

        then:
        logs.warnings.size() == 2
        logs.warnings.findAll { it.contains('s3://bucket-a/root') }.size() == 1
        logs.warnings.findAll { it.contains('s3://bucket-b/root') }.size() == 1
    }
}
