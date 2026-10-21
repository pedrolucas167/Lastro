package io.github.pedrolucas167.lastro.config;

import io.github.pedrolucas167.lastro.domain.Actor;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * O decoder padrao do Spring exige o `issuer-uri`, e nesse modo ele busca o discovery
     * do Keycloak durante o startup -- a aplicacao nao sobe se o Keycloak estiver fora.
     * Aqui o JWKS e buscado sob demanda, e o emissor e a audiencia continuam sendo validados.
     */
    @Bean
    public JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) {
        OAuth2ResourceServerProperties.Jwt jwt = properties.getJwt();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwt.getJwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(jwt.getIssuerUri()),
            new TokenAudienceValidator(jwt.getAudiences())));
        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/actuator/health").permitAll()
                // O cliente M2M faz exatamente o que o load generator precisa: abrir conta e
                // transferir. O titular abre e opera as contas que tem. Todo o resto --
                // movimentar saldo, operar ordens, disparar carga, acionar halt -- e ato de
                // operador, que tambem pode o que os outros dois podem.
                .requestMatchers(HttpMethod.POST, "/api/accounts")
                    .hasAnyRole(Actor.SERVICE, Actor.OPERATOR, Actor.HOLDER)
                .requestMatchers(HttpMethod.POST, "/api/transactions/transfer")
                    .hasAnyRole(Actor.SERVICE, Actor.OPERATOR, Actor.HOLDER)
                // Quem passa aqui pode chamar; se so enxerga as contas que tem, quem decide e
                // o servico -- o back office enxerga qualquer conta.
                .requestMatchers(HttpMethod.GET, "/api/accounts/*", "/api/transactions/*")
                    .hasAnyRole(Actor.HOLDER, Actor.OPERATOR)
                // O extrato e do titular; o balanco do livro inteiro, do operador (cai no anyRequest).
                .requestMatchers(HttpMethod.GET, "/api/accounts/*/statement")
                    .hasAnyRole(Actor.HOLDER, Actor.OPERATOR)
                .requestMatchers(HttpMethod.POST, "/api/accounts/*/holder")
                    .hasRole(Actor.OPERATOR)
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .authenticated()
                .anyRequest().hasRole(Actor.OPERATOR))
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakRealmRolesConverter())));

        return http.build();
    }
}
