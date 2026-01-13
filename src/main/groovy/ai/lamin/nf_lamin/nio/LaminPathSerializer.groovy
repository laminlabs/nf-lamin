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

import nextflow.util.PathSerializer
import nextflow.util.SerializerRegistrant
import org.pf4j.Extension

/**
 * Registers the LaminPath serializer for Kryo serialization.
 *
 * This allows LaminPath objects to be properly serialized and deserialized
 * when passed between Nextflow processes or cached.
 *
 * @author Lamin Labs
 */
@Extension
@CompileStatic
class LaminPathSerializer implements SerializerRegistrant {

    @Override
    void register(Map<Class, Object> serializers) {
        serializers.put(LaminPath, PathSerializer)
    }
}
