package com.capdi.backend.domain.bid.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SelectionCancelRequest {
    @NotNull @Positive
    @JsonProperty("selection_round")
    private Long selectionRound;

    @NotBlank @Size(max=500)
    private String reason;
}
