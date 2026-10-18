package io.github.pedrolucas167.lastro.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * O Keycloak entrega os papeis do realm em {@code realm_access.roles}; o Spring Security
 * espera authorities. O prefixo {@code ROLE_} e o que autoriza por papel.
 */
class KeycloakRealmRolesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        Object roles = realmRoles(jwt);
        if (!(roles instanceof Collection<?> roleNames)) {
            return List.of();
        }
        return roleNames.stream()
            .map(String::valueOf)
            .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
            .toList();
    }

    private Object realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
        return realmAccess == null ? null : realmAccess.get(ROLES);
    }
}
