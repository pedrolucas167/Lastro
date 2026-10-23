package io.github.pedrolucas167.lastro.dto;

/** O motivo da suspensao e opcional: suspender sem justificar tambem e uma decisao registrada. */
public record SuspendClientRequest(String reason) {
}
