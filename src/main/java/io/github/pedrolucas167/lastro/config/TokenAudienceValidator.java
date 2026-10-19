package io.github.pedrolucas167.lastro.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/**
 * A audiencia diz para quem o token serve. O emissor diz de qual realm ele veio -- e isso nao
 * basta: qualquer token que aquele Keycloak emitir (para outro cliente, outro sistema, o
 * console de administracao) valida o mesmo emissor e entraria na API.
 *
 * O {@code aud} chega como texto quando ha uma audiencia e como lista quando ha varias (o
 * Keycloak manda texto no caso comum), entao as duas formas contam.
 *
 * Sem audiencia configurada a API recusa tudo: uma API sem audiencia declarada e uma API que
 * aceita qualquer token do realm, e falhar alto na primeira requisicao e melhor do que servir
 * com a porta aberta.
 */
public class TokenAudienceValidator implements OAuth2TokenValidator<Jwt> {
    private static final String AUDIENCE_CLAIM = "aud";

    private final List<String> accepted;

    public TokenAudienceValidator(List<String> accepted) {
        this.accepted = accepted == null ? List.of() : List.copyOf(accepted);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (accepted.isEmpty()) {
            return failure("the Lastro API has no configured audience "
                + "(spring.security.oauth2.resourceserver.jwt.audiences), so no token is accepted");
        }

        return audiencesOf(token).stream().anyMatch(accepted::contains)
            ? OAuth2TokenValidatorResult.success()
            : failure("the token is not addressed to the Lastro API (aud): expected " + accepted);
    }

    private List<String> audiencesOf(Jwt token) {
        Object claim = token.getClaims().get(AUDIENCE_CLAIM);
        if (claim == null) {
            return List.of();
        }
        if (claim instanceof Collection<?> audiences) {
            return audiences.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(claim));
    }

    private OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
