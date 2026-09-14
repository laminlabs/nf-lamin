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

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output

import software.amazon.awssdk.services.s3.S3Client as AwsS3Client

class LaminS3PathSerializerTest extends Specification {

    static class TestableSerializer extends LaminS3PathSerializer {
        LaminFileSystemProvider provider

        @Override
        protected LaminFileSystemProvider getProvider() { provider }
    }

    LaminS3FileSystem fs = new LaminS3FileSystem(Mock(LaminS3FileSystemProvider), 's3://my-bucket/prefix', Mock(AwsS3Client))

    byte[] serialize(Kryo kryo, LaminS3Path path) {
        def bytes = new ByteArrayOutputStream()
        new Output(bytes).withCloseable { Output output -> kryo.writeObject(output, path) }
        return bytes.toByteArray()
    }

    def "round-trips a path through the managed file system of its storage root"() {
        given:
        def provider = Mock(LaminFileSystemProvider)
        def kryo = new Kryo()
        kryo.register(LaminS3Path, new TestableSerializer(provider: provider))
        def path = new LaminS3Path(fs, 'prefix/results/file.txt')

        when:
        def restored = kryo.readObject(new Input(serialize(kryo, path)), LaminS3Path)

        then:
        1 * provider.getManagedFileSystem('s3://my-bucket/prefix') >> fs
        restored == path
        restored.fileSystem.is(fs)
    }

    def "fails to read a path whose storage root has no managed credentials"() {
        given:
        def provider = Mock(LaminFileSystemProvider) {
            getManagedFileSystem(_) >> { throw new IOException('no credentials') }
        }
        def kryo = new Kryo()
        kryo.register(LaminS3Path, new TestableSerializer(provider: provider))

        when:
        kryo.readObject(new Input(serialize(kryo, new LaminS3Path(fs, 'prefix/a.txt'))), LaminS3Path)

        then:
        def e = thrown(Exception)
        (e instanceof IOException ? e : e.cause).message == 'no credentials'
    }
}
