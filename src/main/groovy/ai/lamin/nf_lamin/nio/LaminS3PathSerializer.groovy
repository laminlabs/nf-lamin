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

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import groovy.transform.CompileStatic
import nextflow.file.FileHelper

/**
 * Kryo serializer for {@link LaminS3Path}, which Nextflow persists in its cache DB.
 *
 * Writes the storage root and object key; on read, the path is rebuilt on the Lamin-managed
 * file system for that root, so it keeps its federated credentials.
 */
@CompileStatic
class LaminS3PathSerializer extends Serializer<LaminS3Path> {

    @Override
    void write(Kryo kryo, Output output, LaminS3Path path) {
        output.writeString(((LaminS3FileSystem) path.fileSystem).storageRoot)
        output.writeString(path.key)
    }

    @Override
    LaminS3Path read(Kryo kryo, Input input, Class<LaminS3Path> type) {
        String storageRoot = input.readString()
        String key = input.readString()
        return new LaminS3Path(getProvider().getManagedFileSystem(storageRoot), key)
    }

    /** The lamin:// provider of this process. Protected so tests can inject one. */
    protected LaminFileSystemProvider getProvider() {
        return LaminFileSystemProvider.installed() ?: FileHelper.getOrInstallProvider(LaminFileSystemProvider)
    }
}
