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
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client as AwsS3Client
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
     * @param awsConfig The Nextflow {@code aws} config scope (accessKey, secretKey, sessionToken,
     *                  profile, region), used for plain {@code s3://} paths; may be null
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
                if (e.statusCode() != 404) {
                    throw e
                }
            }
            // not an object, so treat the key as a prefix
            return listPrefix(client, bucket, key + '/')
        } catch (Exception e) {
            log.debug "Could not read stats of s3://${bucket}/${key}: ${e.message}"
            return null
        }
    }

    private static ArtifactStats listPrefix(AwsS3Client client, String bucket, String prefix) {
        long size = 0
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
                size += object.size() ?: 0L
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
        return LaminHashing.fromEtags(size, etags)
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
        AwsCredentialsProvider credentials = buildCredentials(awsConfig, DefaultCredentialsProvider.create())
        String region = awsConfig.region as String
        return AwsS3Client.builder()
            .crossRegionAccessEnabled(true)
            .region(region ? Region.of(region) : Region.US_EAST_1)
            .credentialsProvider(credentials)
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build()
    }

    /**
     * Credentials for plain {@code s3://} paths: explicit keys or a profile from the Nextflow
     * {@code aws} scope, else the default chain, else anonymous access for public buckets.
     */
    static AwsCredentialsProvider buildCredentials(Map awsConfig, AwsCredentialsProvider defaultChain) {
        Map config = awsConfig ?: [:]
        String accessKey = config.accessKey as String
        String secretKey = config.secretKey as String
        String sessionToken = config.sessionToken as String
        if (accessKey && secretKey) {
            if (sessionToken) {
                return StaticCredentialsProvider.create(AwsSessionCredentials.create(accessKey, secretKey, sessionToken))
            }
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
