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
import groovy.util.logging.Slf4j

import java.nio.file.Files
import java.nio.file.Path

/**
 * An OutputStream that buffers to a temporary file and uploads it to S3 on close.
 *
 * S3 has no append and no streaming PUT of unknown length, so the content has to be
 * complete before the upload starts.
 */
@Slf4j
@CompileStatic
class LaminS3OutputStream extends OutputStream {

    private final LaminS3Uploader uploader
    private final String bucket
    private final String key
    private final Path tempFile
    private final OutputStream delegate

    private boolean closed = false

    LaminS3OutputStream(LaminS3Uploader uploader, String bucket, String key) throws IOException {
        this.uploader = uploader
        this.bucket = bucket
        this.key = key
        this.tempFile = Files.createTempFile('nf-lamin-', '.upload')
        this.tempFile.toFile().deleteOnExit()
        this.delegate = Files.newOutputStream(tempFile)
    }

    @Override
    void write(int b) throws IOException {
        delegate.write(b)
    }

    @Override
    void write(byte[] buffer) throws IOException {
        delegate.write(buffer)
    }

    @Override
    void write(byte[] buffer, int offset, int length) throws IOException {
        delegate.write(buffer, offset, length)
    }

    @Override
    void flush() throws IOException {
        delegate.flush()
    }

    @Override
    void close() throws IOException {
        if (closed) {
            return
        }
        closed = true
        try {
            delegate.close()
            uploader.upload(tempFile, bucket, key)
        }
        finally {
            Files.deleteIfExists(tempFile)
        }
    }
}
