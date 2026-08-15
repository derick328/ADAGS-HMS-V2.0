package com.adags;

import com.adags.support.IntegrationTestSupport;
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

    @Test
    void writesTheResolvedSpecToTheRepoRootApiDirectory() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn();

        String yaml = result.getResponse().getContentAsString();
        assertThat(yaml).isNotBlank();

        Path outputPath = Path.of("..", "api", "openapi.yaml");
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, yaml);
    }
}
