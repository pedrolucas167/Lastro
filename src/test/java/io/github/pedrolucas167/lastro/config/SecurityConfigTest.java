package io.github.pedrolucas167.lastro.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode("invalido")).willThrow(new BadJwtException("token inválido"));
        given(jwtDecoder.decode("operador")).willReturn(jwtWithRoles("lastro-operator"));
        given(jwtDecoder.decode("sem-papel")).willReturn(jwtWithRoles());
    }

    @Test
    @DisplayName("O /actuator/health é público: o HEALTHCHECK do container não tem token")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("O health público não expõe detalhes internos")
    void publicHealthHidesDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components").doesNotExist())
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("A API recusa requisições sem token")
    void apiRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/trading/trades"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A API recusa token inválido (assinatura, emissor ou expiração)")
    void apiRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/trading/trades").header("Authorization", "Bearer invalido"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A API aceita token válido com papel do realm")
    void apiAcceptsKeycloakToken() throws Exception {
        mockMvc.perform(get("/api/trading/trades").header("Authorization", "Bearer operador"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Token válido sem papel do realm não acessa a API")
    void tokenWithoutRealmRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/api/trading/trades").header("Authorization", "Bearer sem-papel"))
            .andExpect(status().isForbidden());
    }

    private Jwt jwtWithRoles(String... roles) {
        return Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("service-account-lastro")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("realm_access", Map.of("roles", List.of(roles)))
            .build();
    }
}
