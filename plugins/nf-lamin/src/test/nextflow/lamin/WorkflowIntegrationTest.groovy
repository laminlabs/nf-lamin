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
package nextflow.lamin

import spock.lang.Specification
import spock.lang.Shared
import spock.lang.IgnoreIf

import nextflow.Session
import nextflow.script.WorkflowMetadata
import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
import nextflow.trace.TraceRecord

/**
 * End-to-end workflow tests
 * Tests the complete integration of the Lamin plugin with mock Nextflow workflows
 *
 * @author Robrecht Cannoodt <robrecht@data-intuitive.com>
 */
class WorkflowIntegrationTest extends Specification {

    @Shared
    String apiKey = System.getenv('LAMIN_STAGING_API_KEY')

    def setup() {
        // Clear static state before each test
        LaminPlugin.@session = null
        LaminPlugin.@config = null
    }

    def cleanup() {
        // Clear static state after each test
        LaminPlugin.@session = null
        LaminPlugin.@config = null
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "should handle complete workflow lifecycle with staging config"() {
        given:
        def workflowMetadata = Mock(nextflow.script.WorkflowMetadata) {
            getRepository() >> "https://github.com/test/repo"
            getCommitId() >> "abc123"
            getRevision() >> "main"
        }
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'laminlabs/lamindata',
                    api_key: apiKey,
                    env: 'staging'
                ]
            ]
            getWorkflowMetadata() >> workflowMetadata
        }
        def observer = new LaminObserver()

        when:
        // Simulate complete workflow lifecycle
        observer.onFlowCreate(session)
        observer.onFlowBegin()

        // Simulate process lifecycle
        def executor = Mock(Object)
        observer.onProcessCreate(executor)

        def handler = Mock(TaskHandler)
        def trace = Mock(TraceRecord)
        observer.onProcessSubmit(handler, trace)
        observer.onProcessStart(handler, trace)
        observer.onProcessComplete(handler, trace)

        observer.onFlowComplete()

        then:
        noExceptionThrown()
        observer.session == session
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "should handle workflow with errors"() {
        given:
        def workflowMetadata = Mock(nextflow.script.WorkflowMetadata) {
            getRepository() >> "https://github.com/test/repo"
            getCommitId() >> "abc123"
            getRevision() >> "main"
        }
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'laminlabs/lamindata',
                    api_key: apiKey,
                    env: 'staging'
                ]
            ]
            getWorkflowMetadata() >> workflowMetadata
        }
        def observer = new LaminObserver()

        when:
        observer.onFlowCreate(session)
        observer.onFlowBegin()

        def trace = Mock(TraceRecord)
        def error = new RuntimeException("Workflow failed")
        observer.onFlowError(trace, error)

        then:
        noExceptionThrown()
        observer.session == session
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "should handle cached process events"() {
        given:
        def workflowMetadata = Mock(nextflow.script.WorkflowMetadata) {
            getRepository() >> "https://github.com/test/repo"
            getCommitId() >> "abc123"
            getRevision() >> "main"
        }
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'laminlabs/lamindata',
                    api_key: apiKey,
                    env: 'staging'
                ]
            ]
            getWorkflowMetadata() >> workflowMetadata
        }
        def observer = new LaminObserver()

        when:
        observer.onFlowCreate(session)

        def handler = Mock(TaskHandler)
        def trace = Mock(TraceRecord)
        observer.onProcessCached(handler, trace)

        then:
        noExceptionThrown()
    }

    def "should create observer factory correctly"() {
        given:
        def factory = new LaminObserverFactory()
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'laminlabs/lamindata',
                    api_key: 'mock-api-key'
                ]
            ]
        }

        when:
        def observers = factory.create(session)

        then:
        observers != null
        observers.size() == 1
        observers[0] instanceof LaminObserver
    }

    def "should handle missing configuration gracefully"() {
        given:
        def session = Mock(Session) {
            config >> [:]
        }
        def observer = new LaminObserver()

        when:
        observer.onFlowCreate(session)

        then:
        // Should handle missing config without crashing during onFlowCreate
        // but will fail when trying to parse config
        thrown(IllegalArgumentException)
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "should handle production configuration in workflow"() {
        given:
        def workflowMetadata = Mock(nextflow.script.WorkflowMetadata) {
            getRepository() >> "https://github.com/test/repo"
            getCommitId() >> "abc123"
            getRevision() >> "main"
        }
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'laminlabs/lamindata',
                    api_key: apiKey,
                    env: 'prod',
                    project: 'test-project'
                ]
            ]
            getWorkflowMetadata() >> workflowMetadata
        }
        def observer = new LaminObserver()

        when:
        observer.onFlowCreate(session)
        observer.onFlowBegin()
        observer.onFlowComplete()

        then:
        noExceptionThrown()
        observer.session == session
    }

    def "should validate workflow with custom retry settings"() {
        given:
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'laminlabs/lamindata',
                    api_key: 'mock-api-key',
                    env: 'staging',
                    max_retries: 10,
                    retry_delay: 500
                ]
            ]
        }

        when:
        LaminPlugin.setSession(session)
        def config = LaminPlugin.getConfig()

        then:
        config.getMaxRetries() == 10
        config.getRetryDelay() == 500
    }

    @IgnoreIf({ !env.LAMIN_STAGING_API_KEY })
    def "should handle multiple process events in sequence"() {
        given:
        def workflowMetadata = Mock(nextflow.script.WorkflowMetadata) {
            getRepository() >> "https://github.com/test/repo"
            getCommitId() >> "abc123"
            getRevision() >> "main"
        }
        def session = Mock(Session) {
            config >> [
                lamin: [
                    instance: 'laminlabs/lamindata',
                    api_key: apiKey,
                    env: 'staging'
                ]
            ]
            getWorkflowMetadata() >> workflowMetadata
        }
        def observer = new LaminObserver()

        when:
        observer.onFlowCreate(session)
        observer.onFlowBegin()

        // Simulate multiple processes
        (1..3).each { i ->
            def executor = Mock(Object)
            observer.onProcessCreate(executor)

            def handler = Mock(TaskHandler)
            def trace = Mock(TraceRecord)
            observer.onProcessSubmit(handler, trace)
            observer.onProcessStart(handler, trace)
            observer.onProcessComplete(handler, trace)
        }

        observer.onFlowComplete()

        then:
        noExceptionThrown()
    }
}
