package io.github.pedrolucas167.lastro.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssignHolderRequest {
    @NotBlank(message = "Holder is required")
    private String holder;
}
