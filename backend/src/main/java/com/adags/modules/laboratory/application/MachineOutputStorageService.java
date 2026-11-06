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

    @org.springframework.beans.factory.annotation.Value("${lab.report.max-file-size-bytes:10485760}")
    private long maxReportBytes = 10L * 1024 * 1024;

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

    public String uploadReportPdf(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > maxReportBytes)
            throw new IllegalArgumentException("Report PDF must be between 1 byte and 10 MB.");
        if (!"application/pdf".equals(file.getContentType()))
            throw new IllegalArgumentException("Report attachment must be a PDF.");
        try (InputStream in = file.getInputStream()) {
            byte[] signature = in.readNBytes(5);
            if (!java.util.Arrays.equals(signature, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                throw new IllegalArgumentException("Report attachment is not a valid PDF.");
        } catch (java.io.IOException e) {
            throw new MachineOutputStorageException("Could not inspect report PDF.", e);
        }
        return upload(file);
    }

    /** A short-lived, directly-fetchable URL for viewing a stored machine output file, e.g. from My History. */
    public String presignedUrl(String objectKey) {
        try {
            // The internal MinIO hostname is unreachable from a user's browser.
            // Signing against the public endpoint also signs the correct Host
            // header; rewriting an already signed URL would invalidate it.
            MinioClient signingClient = properties.getPublicEndpoint() == null
                    || properties.getPublicEndpoint().isBlank()
                    ? minioClient
                    : MinioClient.builder()
                            .endpoint(properties.getPublicEndpoint())
                            .credentials(properties.getAccessKey(), properties.getSecretKey())
                            // This deployment uses MinIO's default region.
                            // Supplying it avoids an outbound bucket-location
                            // request while generating the signed URL.
                            .region("us-east-1")
                            .build();
            return signingClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
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
