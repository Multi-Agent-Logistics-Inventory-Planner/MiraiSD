package com.mirai.inventoryservice.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regenerates packages/contracts/openapi.json from the live /v3/api-docs endpoint (only
 * registered when springdoc.api-docs.enabled=true - see application-test.properties).
 * The generated TypeScript client in packages/api-client reads this file, so it is checked
 * into the repo rather than produced only at Maven build time.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiContractExportTest {

    // services/inventory-service -> repo root is two directories up.
    private static final Path CONTRACT_PATH =
            Path.of("../../packages/contracts/openapi.json").normalize();

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void regeneratesTheOpenApiContract() throws IOException {
        String rawJson = restTemplate.getForObject("/v3/api-docs", String.class);
        assertThat(rawJson).isNotBlank();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode spec = mapper.readTree(rawJson);
        assertThat(spec.has("openapi")).isTrue();
        assertThat(spec.has("paths")).isTrue();

        String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec) + "\n";
        Files.createDirectories(CONTRACT_PATH.getParent());
        Files.writeString(CONTRACT_PATH, pretty, StandardCharsets.UTF_8);
    }
}
