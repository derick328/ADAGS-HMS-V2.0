package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.application.CatalogFileStoragePort;
import com.adags.shared.storage.MinioConfig.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

// Archive of the admin's uploaded .xlsx. Same shape as
// MachineOutputStorageService — the two just target different buckets.
// The batch id names the object so the Apply step re-fetches
// deterministically and an auditor can pull the exact bytes.
@Component
public class MinioCatalogFileStorage implements CatalogFileStoragePort {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioCatalogFileStorage(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public String store(UUID batchId, String originalFilename, byte[] bytes) {
        try {
            ensureBucketExists();
            String objectKey = batchId + "-" + sanitizeFilename(originalFilename);
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(properties.getCatalogImportBucket())
                        .object(objectKey)
                        .stream(in, bytes.length, -1)
                        .contentType(XLSX_CONTENT_TYPE)
                        .build());
            }
            return objectKey;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to archive catalogue import file.", e);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.getCatalogImportBucket())
                    .object(storageKey)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch archived catalogue import file: " + storageKey, e);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(properties.getCatalogImportBucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getCatalogImportBucket()).build());
        }
    }

    private static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) return "catalog.xlsx";
        String base = originalFilename.replace('\\', '/');
        int lastSlash = base.lastIndexOf('/');
        String name = lastSlash >= 0 ? base.substring(lastSlash + 1) : base;
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
