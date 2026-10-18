package io.github.pedrolucas167.lastro.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

/**
 * O realm de desenvolvimento tem credenciais publicas -- o segredo do cliente M2M esta no
 * repositorio. Contra um emissor nao-local isso e uma porta aberta: quem le o segredo pede
 * um token M2M, e o M2M abre conta e transfere. Por isso a aplicacao recusa subir nessa
 * combinacao; o segredo de desenvolvimento so e tolerado com emissor local.
 *
 * O emissor (e nao a URL do JWKS) e o sinal de "este realm e local" porque o Keycloak fixa
 * o `iss` como o mundo o ve -- dentro do Docker o JWKS fica em http://keycloak:8080, mas o
 * emissor continua localhost.
 */
@Component
public class DevelopmentCredentialsGuard implements InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(DevelopmentCredentialsGuard.class);

    private static final String DEVELOPMENT_CLIENT_SECRET = "lastro-service-secret";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private final String clientSecret;
    private final String issuerUri;
    private final boolean reject;

    public DevelopmentCredentialsGuard(
            @Value("${lastro.loadtest.client-secret}") String clientSecret,
            OAuth2ResourceServerProperties resourceServer,
            @Value("${lastro.security.reject-dev-credentials:true}") boolean reject) {
        this.clientSecret = clientSecret;
        this.issuerUri = resourceServer.getJwt().getIssuerUri();
        this.reject = reject;
    }

    @Override
    public void afterPropertiesSet() {
        if (!DEVELOPMENT_CLIENT_SECRET.equals(clientSecret) || isLocalIssuer()) {
            return;
        }

        String diagnosis = ("the development M2M client secret is in use with a non-local issuer (%s), "
            + "and that secret is in the repository. Set your own lastro.loadtest.client-secret (and the "
            + "matching client in your Keycloak), or point the issuer at localhost for a local demo. "
            + "To downgrade this to a warning, set lastro.security.reject-dev-credentials=false")
            .formatted(issuerUri);

        if (reject) {
            throw new IllegalStateException("Refusing to start: " + diagnosis);
        }
        logger.warn("Development credentials accepted: {}", diagnosis);
    }

    private boolean isLocalIssuer() {
        if (issuerUri == null || issuerUri.isBlank()) {
            return false;
        }
        try {
            String host = URI.create(issuerUri).getHost();
            return host != null && LOCAL_HOSTS.stream().anyMatch(local -> local.equalsIgnoreCase(host));
        } catch (IllegalArgumentException notAUri) {
            return false;
        }
    }
}
