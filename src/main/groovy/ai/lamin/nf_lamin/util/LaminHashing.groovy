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

package ai.lamin.nf_lamin.util

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.stream.Stream

import ai.lamin.nf_lamin.model.ArtifactStats
import groovy.transform.CompileStatic

/**
 * Hashes files, directories and S3 objects exactly like lamindb does, so artifacts
 * registered by nf-lamin dedup against artifacts registered by lamindb.
 *
 * Mirrors {@code lamindb_setup/core/hashing.py} and the S3 branch of
 * {@code lamindb_setup/core/upath.py}: every hash is the url-safe base64 of a digest,
 * without padding, truncated to {@link #HASH_LENGTH} characters.
 */
@CompileStatic
class LaminHashing {

    static final int HASH_LENGTH = 22
    static final long DEFAULT_CHUNK_SIZE = 50L * 1024 * 1024

    private static final int BUFFER_SIZE = 1 << 20

    /** Url-safe base64 without padding, not truncated. */
    static String toB64(byte[] digest) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** Hex digest (an S3 etag, quotes allowed) to the base64 form lamindb stores. */
    static String b16ToB64(String hex) {
        return toB64(HexFormat.of().parseHex(stripEtag(hex)))
    }

    static String hashString(String s) {
        return truncate(toB64(md5().digest(s.getBytes(StandardCharsets.UTF_8))))
    }

    /** Combines per-file hashes into a directory hash; order of the input does not matter. */
    static String hashFromHashes(Collection<String> hashes) {
        MessageDigest outer = md5()
        for (String hash : hashes.sort(false)) {
            outer.update(md5().digest(hash.getBytes(StandardCharsets.UTF_8)))
        }
        return truncate(toB64(outer.digest()))
    }

    /**
     * Hash a file: md5 of the content when it fits in one chunk, otherwise sha1 of the
     * sha1s of the first and last chunk ({@code sha1-fl}).
     */
    static ArtifactStats hashFile(Path file, long chunkSize = DEFAULT_CHUNK_SIZE) {
        long size = Files.size(file)
        try (SeekableByteChannel channel = Files.newByteChannel(file)) {
            if (size <= chunkSize) {
                MessageDigest md = md5()
                digestRange(channel, 0, size, md)
                return new ArtifactStats(size, truncate(toB64(md.digest())), 'md5', null)
            }
            MessageDigest first = sha1()
            digestRange(channel, 0, chunkSize, first)
            MessageDigest last = sha1()
            digestRange(channel, size - chunkSize, chunkSize, last)
            MessageDigest outer = sha1()
            outer.update(first.digest())
            outer.update(last.digest())
            return new ArtifactStats(size, truncate(toB64(outer.digest())), 'sha1-fl', null)
        }
    }

    /** Hash a directory ({@code md5-d}); an empty directory has a size and count but no hash. */
    static ArtifactStats hashDir(Path dir, long chunkSize = DEFAULT_CHUNK_SIZE) {
        long size = 0
        List<String> hashes = []
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter { Path p -> Files.isRegularFile(p) }.toList()) {
                ArtifactStats stats = hashFile(file, chunkSize)
                size += stats.size
                hashes.add(stats.hash)
            }
        }
        if (hashes.isEmpty()) {
            return new ArtifactStats(0L, null, null, 0L)
        }
        return new ArtifactStats(size, hashFromHashes(hashes), 'md5-d', hashes.size() as Long)
    }

    /** The per-object hash lamindb derives from an S3 etag; the multipart part count is dropped. */
    static String etagHash(String etag) {
        String clean = stripEtag(etag)
        if (!clean) {
            return null
        }
        return truncate(b16ToB64(clean.split('-')[0]))
    }

    /** Stats of a single S3 object: {@code md5} for a plain etag, {@code md5-<N>} for a multipart one. */
    static ArtifactStats fromEtag(long size, String etag) {
        String clean = stripEtag(etag)
        if (!clean) {
            return new ArtifactStats(size, null, null, null)
        }
        String[] parts = clean.split('-')
        String hashType = parts.length > 1 ? 'md5-' + parts[1] : 'md5'
        return new ArtifactStats(size, etagHash(clean), hashType, null)
    }

    /** Stats of an S3 prefix, combined like a local directory ({@code md5-d}). */
    static ArtifactStats fromEtags(long totalSize, Collection<String> etags) {
        List<String> hashes = etags.collect { String etag -> etagHash(etag) }
        return new ArtifactStats(totalSize, hashFromHashes(hashes), 'md5-d', etags.size() as Long)
    }

    private static String stripEtag(String etag) {
        return etag == null ? '' : etag.replaceAll('["=]', '')
    }

    private static String truncate(String hash) {
        return hash.length() > HASH_LENGTH ? hash.substring(0, HASH_LENGTH) : hash
    }

    private static void digestRange(SeekableByteChannel channel, long offset, long length, MessageDigest md) {
        channel.position(offset)
        ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(BUFFER_SIZE, Math.max(length, 1)))
        long remaining = length
        while (remaining > 0) {
            buffer.clear()
            if (remaining < buffer.capacity()) {
                buffer.limit((int) remaining)
            }
            int read = channel.read(buffer)
            if (read < 0) {
                break
            }
            buffer.flip()
            md.update(buffer)
            remaining -= read
        }
    }

    private static MessageDigest md5() {
        return MessageDigest.getInstance('MD5')
    }

    private static MessageDigest sha1() {
        return MessageDigest.getInstance('SHA-1')
    }
}
