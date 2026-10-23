package io.github.pedrolucas167.lastro.dto;

import io.github.pedrolucas167.lastro.domain.Client;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class ClientResponse {
    private String id;
    /** O `sub` do Keycloak: e por ele que a conta encontra o cliente. */
    private String subject;
    private String name;
    private String status;
    /** Por que esta suspenso; nulo quando ativo. */
    private String suspensionReason;
    private Instant registeredAt;

    public static ClientResponse of(Client client) {
        return new ClientResponse(client.getId(), client.getSubject(), client.getName(),
            client.getStatus().name(), client.getSuspensionReason(), client.getRegisteredAt());
    }
}
