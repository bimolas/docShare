package com.subject77.docshare.config.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.List;

@Configuration
public class S3Config {

    private static final Logger log = LoggerFactory.getLogger(S3Config.class);

    @Value("${aws.s3.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${aws.s3.access-key:minio_admin}")
    private String accessKey;

    @Value("${aws.s3.secret-key:MinioSecretPassword123!}")
    private String secretKey;

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    @Value("${aws.s3.bucket-name:docshare-bucket}")
    private String bucketName;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    /**
     * Runs at startup: creates the S3 bucket if absent and applies CORS rules
     * that allow the Angular frontend to PUT files directly to MinIO.
     * Without CORS, browsers silently block the PUT and Angular reports a
     * JSON parse error on the empty/opaque CORS rejection response.
     */
    @Bean
    public BucketInitializer bucketInitializer(S3Client s3Client) {
        return new BucketInitializer(s3Client, bucketName);
    }

    public static class BucketInitializer {

        private static final Logger log = LoggerFactory.getLogger(BucketInitializer.class);
        private final S3Client s3Client;
        private final String bucketName;

        public BucketInitializer(S3Client s3Client, String bucketName) {
            this.s3Client = s3Client;
            this.bucketName = bucketName;
            init();
        }

        private void init() {
            ensureBucketExists();
            applyCorsConfiguration();
        }

        private void ensureBucketExists() {
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
                log.info("S3 bucket '{}' already exists.", bucketName);
            } catch (NoSuchBucketException e) {
                log.info("S3 bucket '{}' not found — creating it.", bucketName);
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                log.info("S3 bucket '{}' created successfully.", bucketName);
            } catch (Exception e) {
                log.warn("Could not verify or create bucket '{}': {}", bucketName, e.getMessage());
            }
        }

        private void applyCorsConfiguration() {
            try {
                CORSRule corsRule = CORSRule.builder()
                        .allowedOrigins(
                                "http://localhost:4200",
                                "http://localhost:4000",
                                "http://127.0.0.1:4200"
                        )
                        .allowedMethods("PUT", "GET", "HEAD", "POST", "DELETE")
                        .allowedHeaders("*")
                        .exposeHeaders("ETag", "Content-Length")
                        .maxAgeSeconds(3600)
                        .build();

                s3Client.putBucketCors(PutBucketCorsRequest.builder()
                        .bucket(bucketName)
                        .corsConfiguration(CORSConfiguration.builder()
                                .corsRules(List.of(corsRule))
                                .build())
                        .build());

                log.info("CORS configuration applied to S3 bucket '{}'.", bucketName);
            } catch (Exception e) {
                log.warn("Could not apply CORS configuration to bucket '{}': {}", bucketName, e.getMessage());
            }
        }
    }
}
