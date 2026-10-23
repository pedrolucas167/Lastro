package io.github.pedrolucas167.lastro.dto;

import jakarta.validation.constraints.NotBlank;

/** O cliente novo: o `sub` do Keycloak e o nome que o operador cadastra. */
public record RegisterClientRequest(
        @NotBlank(message = "subject is required") String subject,
        @NotBlank(message = "name is required") String name) {
}
