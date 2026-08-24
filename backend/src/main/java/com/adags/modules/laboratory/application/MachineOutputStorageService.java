package com.adags.modules.laboratory.application;

import com.adags.shared.storage.MinioConfig.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// 15-lab-technician.md §5.4 / 16-lab-verification-model.md v0.2 §4: the
// machine output PDF/scan goes to object storage, the database keeps only
// the key. Not a Spring @Repository — this is I/O against an external
// object store, not the relational database, so it lives in application
// rather than infrastructure's JDBC-repository sense; still no domain logic
// here, so it's a thin @Service wrapper, same tier RecordLabResultService
// sits at.
@Service
public class MachineOutputStorageService {

    private static final int PRESIGNED_URL_EXPIRY_MINUTES = 15;

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MachineOutputStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public String upload(MultipartFile file) {
        try {
            ensureBucketExists();
            String objectKey = UUID.randomUUID() + "-" + sanitizeFilename(file.getOriginalFilename());
            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(properties.getLabMachineOutputBucket())
                        .object(objectKey)
                        .stream(in, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }
            return objectKey;
        } catch (Exception e) {
            throw new MachineOutputStorageException("Failed to store machine output.", e);
        }
    }

    /** A short-lived, directly-fetchable URL for viewing a stored machine output file, e.g. from My History. */
    public String presignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getLabMachineOutputBucket())
                    .object(objectKey)
                    .expiry(PRESIGNED_URL_EXPIRY_MINUTES, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            throw new MachineOutputStorageException("Failed to presign machine output URL.", e);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(properties.getLabMachineOutputBucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getLabMachineOutputBucket()).build());
        }
    }

    // Object keys are flat (no directories) so they round-trip cleanly
    // through a REST path variable — strip any path separators a hostile
    // or unusual filename might carry rather than trusting client input.
    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "machine-output";
        }
        String base = originalFilename.replace('\\', '/');
        int lastSlash = base.lastIndexOf('/');
        String name = lastSlash >= 0 ? base.substring(lastSlash + 1) : base;
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
