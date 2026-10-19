package io.github.pedrolucas167.lastro.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenAudienceValidatorTest {

    @Test
    @DisplayName("O `aud` de uma audiencia só — o formato que o Keycloak manda — é aceito")
    void acceptsTheSingleAudienceKeycloakSends() {
        OAuth2TokenValidatorResult result = new TokenAudienceValidator(List.of("lastro-api"))
            .validate(jwt(Map.of("aud", "lastro-api")));

        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("Com várias audiencias, basta a da API estar entre elas")
    void acceptsAnAudienceListContainingTheApi() {
        OAuth2TokenValidatorResult result = new TokenAudienceValidator(List.of("lastro-api"))
            .validate(jwt(Map.of("aud", List.of("account", "lastro-api"))));

        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("Token emitido para outra audiencia é recusado")
    void rejectsAnotherAudience() {
        OAuth2TokenValidatorResult result = new TokenAudienceValidator(List.of("lastro-api"))
            .validate(jwt(Map.of("aud", List.of("account", "outro-sistema"))));

        assertTrue(result.hasErrors());
        assertEquals("invalid_token", result.getErrors().iterator().next().getErrorCode());
    }

    @Test
    @DisplayName("Token sem audiencia nenhuma é recusado")
    void rejectsTokenWithoutAudience() {
        assertTrue(new TokenAudienceValidator(List.of("lastro-api")).validate(jwt(Map.of())).hasErrors());
    }

    @Test
    @DisplayName("Sem audiencia configurada a API recusa tudo, em vez de aceitar qualquer token")
    void recusaTudoSemAudienciaConfigurada() {
        assertTrue(new TokenAudienceValidator(List.of()).validate(jwt(Map.of("aud", "lastro-api"))).hasErrors());
        assertTrue(new TokenAudienceValidator(null).validate(jwt(Map.of("aud", "lastro-api"))).hasErrors());
    }

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("operador")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claims(c -> c.putAll(claims))
            .build();
    }
}
