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

package ai.lamin.nf_lamin.nio

import spock.lang.Specification

import java.util.function.Supplier

import ai.lamin.nf_lamin.hub.CloudAccessResponse

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials

class LaminCloudCredentialsProviderTest extends Specification {

    private static CloudAccessResponse access(String suffix) {
        return new CloudAccessResponse([
            Credentials: [
                AccessKeyId: "AKIA${suffix}",
                SecretAccessKey: "secret${suffix}",
                SessionToken: "token${suffix}"
            ],
            StorageAccessibility: [role: 'write']
        ] as Map<String, Object>)
    }

    def "should ask the supplier on every resolve, so rotated credentials are picked up"() {
        given:
        def responses = [access('1'), access('2')] as LinkedList
        def provider = new LaminCloudCredentialsProvider({ responses.poll() } as Supplier<CloudAccessResponse>)

        expect:
        (provider.resolveCredentials() as AwsSessionCredentials).sessionToken() == 'token1'
        (provider.resolveCredentials() as AwsSessionCredentials).sessionToken() == 'token2'
    }

    def "should fail when the supplier has no usable credentials"() {
        given:
        def provider = new LaminCloudCredentialsProvider({ null } as Supplier<CloudAccessResponse>)

        when:
        provider.resolveCredentials()

        then:
        thrown(IllegalStateException)
    }

    def "should reject a null supplier"() {
        when:
        new LaminCloudCredentialsProvider(null)

        then:
        thrown(IllegalArgumentException)
    }
}
