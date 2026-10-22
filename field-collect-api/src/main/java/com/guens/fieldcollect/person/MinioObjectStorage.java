package com.guens.fieldcollect.person;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/** Stockage MinIO (compatible S3). Le bucket est créé au premier usage s'il n'existe pas. */
@Component
@ConditionalOnExpression("!'${app.storage.minio.access-key:}'.isEmpty()")
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketReady;

    public MinioObjectStorage(@Value("${app.storage.minio.endpoint}") String endpoint,
                              @Value("${app.storage.minio.access-key}") String accessKey,
                              @Value("${app.storage.minio.secret-key}") String secretKey,
                              @Value("${app.storage.minio.bucket}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    private void ensureBucket() throws Exception {
        if (bucketReady) return;
        synchronized (this) {
            if (!bucketReady) {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                bucketReady = true;
            }
        }
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(new ByteArrayInputStream(data), data.length, -1).contentType(contentType).build());
        } catch (Exception e) {
            throw new IllegalStateException("Stockage MinIO indisponible", e);
        }
    }

    @Override
    public byte[] get(String key) {
        try (GetObjectResponse r = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return r.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Lecture MinIO impossible", e);
        }
    }
}
