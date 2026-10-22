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

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

import ai.lamin.nf_lamin.model.ArtifactStats
import ai.lamin.nf_lamin.nio.LaminS3FileSystem
import ai.lamin.nf_lamin.nio.LaminS3Path
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client
import software.amazon.awssdk.services.s3.S3ClientBuilder
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.S3Object

/**
 * Computes {@link ArtifactStats} for a path the way lamindb would, so the hub can store
 * size and hash for artifacts in storage it cannot read itself.
 *
 * S3 objects are described by a HeadObject call, S3 prefixes by listing them; local paths
 * are hashed from their content. Anything else, and any failure, yields {@code null}, which
 * means "let the server compute the stats".
 */
@Slf4j
@CompileStatic
class ArtifactStatsResolver implements Closeable {

    private final Map awsConfig
    private final ConcurrentHashMap<String, Optional<ArtifactStats>> cache = new ConcurrentHashMap<>()
    private final Object clientLock = new Object()
    private AwsS3Client defaultClient

    /**
     * @param awsConfig The Nextflow {@code aws} config scope (accessKey, secretKey, profile, region
     *                  and client.endpoint / s3PathStyleAccess / anonymous), used for plain
     *                  {@code s3://} paths; may be null
     */
    ArtifactStatsResolver(Map awsConfig = null) {
        this.awsConfig = awsConfig ?: [:]
    }

    /**
     * Stats for a path, or null when they cannot be computed client-side.
     *
     * @param path  A storage path; {@code lamin-s3://} paths use their federated client
     * @param cache Whether to reuse a previous result for the same path (inputs are consumed
     *              by many tasks; outputs may be overwritten during the run)
     */
    ArtifactStats resolve(Path path, boolean cache) {
        if (path == null) {
            return null
        }
        String uri = PathUtils.toStorageUri(path)
        if (cache) {
            Optional<ArtifactStats> hit = this.cache.get(uri)
            if (hit != null) {
                return hit.orElse(null)
            }
        }
        ArtifactStats stats = compute(path, uri)
        if (cache) {
            this.cache.put(uri, Optional.ofNullable(stats))
        } else {
            // the path may have been overwritten, so later inputs must not reuse older stats
            this.cache.remove(uri)
        }
        return stats
    }

    /**
     * Stats for an S3 object or prefix, using the default client; null on any failure.
     */
    ArtifactStats resolveS3(String bucket, String key) {
        try {
            return statS3(bucket, key, getDefaultClient())
        } catch (Exception e) {
            log.debug "Could not create an S3 client for s3://${bucket}/${key}: ${e.message}"
            return null
        }
    }

    @Override
    void close() {
        synchronized (clientLock) {
            defaultClient?.close()
            defaultClient = null
        }
    }

    private ArtifactStats compute(Path path, String uri) {
        try {
            // lamin-s3 paths carry federated credentials for their storage location
            if (path instanceof LaminS3Path) {
                LaminS3Path s3Path = (LaminS3Path) path
                AwsS3Client client = ((LaminS3FileSystem) s3Path.fileSystem).s3Client
                return statS3(s3Path.bucket, s3Path.key, client)
            }
            URI parsed = new URI(uri)
            String scheme = parsed.scheme ?: 'file'
            if (scheme == 's3') {
                String bucket = parsed.host ?: parsed.authority
                String key = (parsed.path ?: '').replaceFirst('^/', '')
                return resolveS3(bucket, key)
            }
            if (scheme == 'file') {
                return Files.isDirectory(path) ? LaminHashing.hashDir(path) : LaminHashing.hashFile(path)
            }
            log.debug "No client-side stats for ${uri}: scheme '${scheme}' is left to the server"
            return null
        } catch (Exception e) {
            log.debug "Could not compute stats for ${uri}: ${e.message}"
            return null
        }
    }

    private static ArtifactStats statS3(String bucket, String rawKey, AwsS3Client client) {
        String key = (rawKey ?: '').replaceAll('/+$', '')
        if (!bucket || !key) {
            // a bare bucket is never an artifact
            return null
        }
        try {
            try {
                HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()
                HeadObjectResponse head = client.headObject(request)
                return LaminHashing.fromEtag(head.contentLength() ?: 0L, head.eTag())
            } catch (S3Exception e) {
                // without s3:ListBucket on the whole bucket, S3 answers 403 instead of 404 for a missing key
                if (e.statusCode() != 404 && e.statusCode() != 403) {
                    throw e
                }
            }
            // not a (readable) object, so treat the key as a prefix
            return listPrefix(client, bucket, key + '/')
        } catch (Exception e) {
            log.debug "Could not read stats of s3://${bucket}/${key}: ${e.message}"
            return null
        }
    }

    private static ArtifactStats listPrefix(AwsS3Client client, String bucket, String prefix) {
        long size = 0
        long fileCount = 0
        List<String> etags = []
        String token = null
        while (true) {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .continuationToken(token)
                .build()
            ListObjectsV2Response page = client.listObjectsV2(request)
            for (S3Object object : page.contents()) {
                long objectSize = object.size() ?: 0L
                // zero-byte folder markers stay in the hash but are not files
                if (objectSize != 0L || !object.key().endsWith('/')) {
                    fileCount++
                }
                size += objectSize
                etags.add(object.eTag())
            }
            if (!page.isTruncated() || !page.nextContinuationToken()) {
                break
            }
            token = page.nextContinuationToken()
        }
        if (etags.isEmpty()) {
            return null
        }
        return LaminHashing.fromEtags(size, etags, fileCount)
    }

    private AwsS3Client getDefaultClient() {
        synchronized (clientLock) {
            if (defaultClient == null) {
                defaultClient = createDefaultClient()
            }
            return defaultClient
        }
    }

    /**
     * Client for plain {@code s3://} paths; overridable in tests.
     */
    protected AwsS3Client createDefaultClient() {
        S3ClientSettings settings = clientSettings(awsConfig)
        S3ClientBuilder builder = AwsS3Client.builder()
        builder.credentialsProvider(buildCredentials(awsConfig, DefaultCredentialsProvider.create()))
        builder.region(settings.region ? Region.of(settings.region) : Region.US_EAST_1)
        builder.httpClientBuilder(UrlConnectionHttpClient.builder())
        if (settings.endpoint != null) {
            // an S3-compatible store (MinIO, Ceph, R2): no cross-region redirects
            builder.endpointOverride(settings.endpoint)
            builder.forcePathStyle(settings.pathStyle)
        } else {
            builder.crossRegionAccessEnabled(true)
        }
        return builder.build()
    }

    /** The S3 client options of the Nextflow {@code aws} scope that matter for reading stats. */
    static final class S3ClientSettings {
        final String region
        final URI endpoint
        final boolean pathStyle
        final boolean anonymous

        S3ClientSettings(String region, URI endpoint, boolean pathStyle, boolean anonymous) {
            this.region = region
            this.endpoint = endpoint
            this.pathStyle = pathStyle
            this.anonymous = anonymous
        }
    }

    /**
     * Reads {@code aws.region} and {@code aws.client.endpoint} / {@code s3PathStyleAccess} /
     * {@code anonymous}, the options nf-amazon uses for the same purpose.
     */
    static S3ClientSettings clientSettings(Map awsConfig) {
        Map config = awsConfig ?: [:]
        Map client = config.client instanceof Map ? (Map) config.client : [:]
        String endpoint = client.endpoint as String
        return new S3ClientSettings(
            config.region as String,
            endpoint ? URI.create(endpoint) : null,
            (client.s3PathStyleAccess as Boolean) ?: false,
            (client.anonymous as Boolean) ?: false
        )
    }

    /**
     * Credentials for plain {@code s3://} paths: anonymous when asked, else explicit keys or a
     * profile from the Nextflow {@code aws} scope, else the default chain, else anonymous
     * access for public buckets.
     */
    static AwsCredentialsProvider buildCredentials(Map awsConfig, AwsCredentialsProvider defaultChain) {
        Map config = awsConfig ?: [:]
        if (clientSettings(config).anonymous) {
            return AnonymousCredentialsProvider.create()
        }
        String accessKey = config.accessKey as String
        String secretKey = config.secretKey as String
        if (accessKey && secretKey) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        }
        if (config.profile) {
            return ProfileCredentialsProvider.create(config.profile as String)
        }
        try {
            defaultChain.resolveCredentials()
            return defaultChain
        } catch (SdkClientException e) {
            log.debug "No AWS credentials found, reading S3 stats anonymously: ${e.message}"
            return AnonymousCredentialsProvider.create()
        }
    }
}
