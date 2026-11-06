package com.adags.shared.storage;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 15-lab-technician.md §5.4 / 16-lab-verification-model.md v0.2 §4: the
// object store MinIO client, pointed at the "minio" service already in
// docker-compose.yml. Shared infra (like the JDBC template or the Redis
// connection factory), not a module — any module needing object storage
// later injects MinioClient the same way.
@Configuration
public class MinioConfig {

    @Bean
    @ConfigurationProperties(prefix = "adags.minio")
    public MinioProperties minioProperties() {
        return new MinioProperties();
    }

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    public static class MinioProperties {
        private String endpoint;
        private String publicEndpoint;
        private String accessKey;
        private String secretKey;
        private String labMachineOutputBucket;
        private String catalogImportBucket;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getPublicEndpoint() {
            return publicEndpoint;
        }

        public void setPublicEndpoint(String publicEndpoint) {
            this.publicEndpoint = publicEndpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getLabMachineOutputBucket() {
            return labMachineOutputBucket;
        }

        public void setLabMachineOutputBucket(String labMachineOutputBucket) {
            this.labMachineOutputBucket = labMachineOutputBucket;
        }

        public String getCatalogImportBucket() {
            return catalogImportBucket;
        }

        public void setCatalogImportBucket(String catalogImportBucket) {
            this.catalogImportBucket = catalogImportBucket;
        }
    }
}
