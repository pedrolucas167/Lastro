package io.github.pedrolucas167.lastro.dto;

import lombok.Data;

/** Motivo do halt; opcional, porque um halt sem justificativa tambem vale. */
@Data
public class HaltRequest {
    private String reason;
}
