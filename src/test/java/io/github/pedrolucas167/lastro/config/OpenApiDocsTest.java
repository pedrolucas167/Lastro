package io.github.pedrolucas167.lastro.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${lastro.version}")
    private String version;

    @Test
    @DisplayName("A especificação OpenAPI é servida com a versão do build e os metadados do projeto")
    void servesOpenApiSpecification() throws Exception {
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"),
            "versão não filtrada pelo Maven no build: " + version);

        mockMvc.perform(get("/v3/api-docs").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Lastro API"))
            .andExpect(jsonPath("$.info.version").value(version))
            .andExpect(jsonPath("$.info.license.name").value("Apache License 2.0"))
            .andExpect(jsonPath("$.paths['/api/trading/orders']").exists())
            .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme").value("bearer"))
            .andExpect(jsonPath("$.security[0]['bearer-jwt']").exists());
    }
}
