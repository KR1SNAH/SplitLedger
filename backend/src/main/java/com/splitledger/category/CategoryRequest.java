package com.splitledger.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "must be a hex color like #22c55e") String color) {
}
