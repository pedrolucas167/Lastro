package io.github.pedrolucas167.lastro.domain;

import java.util.Set;

/**
 * Quem esta chamando a API: a identidade do token (`sub`) e os papeis que ele carregava.
 *
 * Os papeis viajam junto com o ator para o journal -- sao eles que dizem, no futuro, o que o
 * chamador podia fazer naquele momento. Papel revogado depois nao reescreve o passado, e sem
 * isso a auditoria dependeria de perguntar ao Keycloak como o mundo era antes.
 *
 * Privilegiado e o back office -- o operador e o servico M2M -- que opera qualquer conta.
 * Quem nao e privilegiado so opera as contas que tem.
 */
public record Actor(String subject, Set<String> roles) {

    public static final String SERVICE = "lastro-service";
    public static final String OPERATOR = "lastro-operator";
    public static final String HOLDER = "lastro-holder";

    public Actor {
        // O ator vai para o journal: ninguem depois pode mexer nos papeis dele.
        roles = Set.copyOf(roles);
    }

    public boolean privileged() {
        return roles.contains(SERVICE) || roles.contains(OPERATOR);
    }
}
