package io.github.pedrolucas167.lastro.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentCredentialsGuardTest {
    private static final String DEVELOPMENT_SECRET = "lastro-service-secret";
    private static final String OWN_SECRET = "segredo-proprio-do-operador";

    private DevelopmentCredentialsGuard guard(String clientSecret, String issuerUri, boolean reject) {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(issuerUri);
        return new DevelopmentCredentialsGuard(clientSecret, properties, reject);
    }

    @Test
    @DisplayName("Segredo de desenvolvimento com emissor local é permitido")
    void allowsDevelopmentSecretWithLocalIssuer() {
        assertDoesNotThrow(() -> guard(DEVELOPMENT_SECRET, "http://localhost:8090/realms/lastro", true)
            .afterPropertiesSet());
        assertDoesNotThrow(() -> guard(DEVELOPMENT_SECRET, "http://127.0.0.1:8090/realms/lastro", true)
            .afterPropertiesSet());
        assertDoesNotThrow(() -> guard(DEVELOPMENT_SECRET, "http://[::1]:8090/realms/lastro", true)
            .afterPropertiesSet());
    }

    @Test
    @DisplayName("Segredo de desenvolvimento com emissor não-local derruba a subida")
    void refusesDevelopmentSecretWithNonLocalIssuer() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
            () -> guard(DEVELOPMENT_SECRET, "https://sso.empresa.com/realms/lastro", true).afterPropertiesSet());

        assertTrue(refused.getMessage().contains("lastro.loadtest.client-secret"));
        assertTrue(refused.getMessage().contains("https://sso.empresa.com/realms/lastro"));
    }

    @Test
    @DisplayName("Segredo de desenvolvimento sem emissor configurado derruba a subida")
    void refusesDevelopmentSecretWithoutIssuer() {
        assertThrows(IllegalStateException.class,
            () -> guard(DEVELOPMENT_SECRET, null, true).afterPropertiesSet());
        assertThrows(IllegalStateException.class,
            () -> guard(DEVELOPMENT_SECRET, "  ", true).afterPropertiesSet());
    }

    @Test
    @DisplayName("Segredo próprio passa com qualquer emissor")
    void allowsOwnSecretWithAnyIssuer() {
        assertDoesNotThrow(() -> guard(OWN_SECRET, "https://sso.empresa.com/realms/lastro", true)
            .afterPropertiesSet());
        assertDoesNotThrow(() -> guard(OWN_SECRET, null, true).afterPropertiesSet());
    }

    @Test
    @DisplayName("Com o opt-in explícito, a combinação insegura vira aviso")
    void warnsInsteadOfFailingWhenOptedOut() {
        assertDoesNotThrow(() -> guard(DEVELOPMENT_SECRET, "https://sso.empresa.com/realms/lastro", false)
            .afterPropertiesSet());
    }
}
