package io.github.pedrolucas167.lastro.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakRealmRolesConverterTest {

    private final KeycloakRealmRolesConverter converter = new KeycloakRealmRolesConverter();

    @Test
    @DisplayName("Os papéis de realm_access.roles viram authorities com prefixo ROLE_")
    void mapsRealmRolesToAuthorities() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of(
            "realm_access", Map.of("roles", List.of("lastro-service", "lastro-operator")))));

        assertEquals(
            List.of("ROLE_lastro-service", "ROLE_lastro-operator"),
            token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @Test
    @DisplayName("Token sem realm_access não concede authority nenhuma")
    void tokenWithoutRealmAccessHasNoAuthorities() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of()));

        assertTrue(token.getAuthorities().isEmpty());
    }

    @Test
    @DisplayName("realm_access sem a lista de papéis não quebra a conversão")
    void realmAccessWithoutRolesHasNoAuthorities() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of("realm_access", Map.of())));

        assertTrue(token.getAuthorities().isEmpty());
    }

    @Test
    @DisplayName("O nome do portador vira o subject do token")
    void keepsSubjectAsName() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of()));

        assertEquals("service-account-lastro-service", token.getName());
    }

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("service-account-lastro-service")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claims(c -> c.putAll(claims))
            .build();
    }
}
