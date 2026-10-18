package io.github.pedrolucas167.lastro.config;

import io.github.pedrolucas167.lastro.domain.Actor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * O vocabulario de papeis do realm e a unica regra de "quem e privilegiado".
 *
 * O back office -- operador e servico M2M -- opera qualquer conta. Quem nao e privilegiado
 * so opera as contas que tem. Os nomes ficam aqui porque a matriz de rotas (SecurityConfig)
 * e a resolucao do ator precisam concordar sobre eles.
 */
public final class Roles {
    public static final String SERVICE = "lastro-service";
    public static final String OPERATOR = "lastro-operator";
    public static final String HOLDER = "lastro-holder";

    private static final String PREFIX = "ROLE_";

    private Roles() {
    }

    public static Actor actor(Authentication authentication) {
        return new Actor(authentication.getName(), isPrivileged(authentication));
    }

    public static boolean isPrivileged(Authentication authentication) {
        Set<String> authorities = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        return authorities.contains(PREFIX + SERVICE) || authorities.contains(PREFIX + OPERATOR);
    }
}
