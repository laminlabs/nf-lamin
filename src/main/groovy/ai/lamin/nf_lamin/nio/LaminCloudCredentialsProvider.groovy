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

import groovy.transform.CompileStatic

import java.util.function.Supplier

import ai.lamin.nf_lamin.hub.CloudAccessResponse

import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials

/**
 * Supplies LaminHub session credentials to the AWS SDK.
 *
 * The SDK resolves credentials on every request, so a long upload or download picks up
 * rotated credentials as LaminHub issues them, instead of failing halfway through with an
 * expired token.
 */
@CompileStatic
class LaminCloudCredentialsProvider implements AwsCredentialsProvider {

    private final Supplier<CloudAccessResponse> supplier

    LaminCloudCredentialsProvider(Supplier<CloudAccessResponse> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("Credentials supplier cannot be null")
        }
        this.supplier = supplier
    }

    @Override
    AwsCredentials resolveCredentials() {
        CloudAccessResponse access = supplier.get()
        if (access == null || !access.hasCredentials()) {
            throw new IllegalStateException("LaminHub returned no usable cloud credentials")
        }
        return AwsSessionCredentials.create(access.accessKeyId, access.secretAccessKey, access.sessionToken)
    }
}
