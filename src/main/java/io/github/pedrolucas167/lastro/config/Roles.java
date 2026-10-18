package io.github.pedrolucas167.lastro.config;

import io.github.pedrolucas167.lastro.domain.Actor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Le o token como um {@link Actor}: o `sub` e os papeis que vieram como authorities.
 *
 * O Spring prefixa as authorities com `ROLE_`; o ator guarda o nome do papel do realm, que e o
 * que o journal registra e o que a matriz de rotas (SecurityConfig) usa.
 */
public final class Roles {
    private static final String PREFIX = "ROLE_";

    private Roles() {
    }

    public static Actor actor(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith(PREFIX))
            .map(authority -> authority.substring(PREFIX.length()))
            .collect(Collectors.toSet());
        return new Actor(authentication.getName(), roles);
    }
}
