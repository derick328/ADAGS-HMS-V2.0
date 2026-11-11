package com.adags;

import com.adags.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Run via the `generateOpenApiDocs` Gradle task (see build.gradle.kts), not
// as part of the ordinary test suite — it writes a file as a side effect,
// which a unit test normally shouldn't do.
@AutoConfigureMockMvc
class OpenApiSpecGenerationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void writesTheResolvedSpecToTheRepoRootApiDirectory() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn();

        String yaml = result.getResponse().getContentAsString();
        assertThat(yaml).isNotBlank();

        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode paths = document.path("paths");
        assertThat(paths.has("/api/admin/catalog/items/{itemId}/price")).isTrue();
        assertThat(paths.has("/api/admin/catalog/items/export")).isTrue();
        assertThat(paths.has("/api/lab-orders/mine/{id}")).isTrue();
        assertThat(paths.has("/api/lab-orders/mine/unread-result-orders")).isTrue();
        assertThat(paths.has("/api/lab-results/by-lab-order/{labOrderId}")).isTrue();
        assertThat(document.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
        assertThat(paths.path("/api/admin/catalog/items/{itemId}/price").path("put").path("security").toString())
                .contains("bearerAuth");
        assertThat(paths.path("/api/admin/catalog/items/export").path("get").path("responses")
                .path("200").path("content")
                .path("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .path("schema").path("format").asText()).isEqualTo("binary");
        assertThat(document.path("components").path("schemas").path("CollectSpecimenRequest")
                .path("required").toString()).doesNotContain("sampleTypeOverrideReason");

        Path outputPath = Path.of("..", "api", "openapi.yaml");
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, yaml);
    }
}
