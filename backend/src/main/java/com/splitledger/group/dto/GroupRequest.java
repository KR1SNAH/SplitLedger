package com.splitledger.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create or update a group. A null currency means "the creator's currency" on create and "unchanged" on update. */
public record GroupRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 300) String description,
        @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code") String currency) {
}
