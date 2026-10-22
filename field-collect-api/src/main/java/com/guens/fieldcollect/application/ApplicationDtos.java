package com.guens.fieldcollect.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ApplicationDtos {
    private ApplicationDtos() {}

    public record ApplicationRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "lettres, chiffres, _ et - uniquement") String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description,
            Boolean active) {}

    public record ApplicationResponse(Long id, String code, String name, String description,
                                      boolean active, List<Long> cityIds) {}

    public record CityIdsRequest(@NotNull List<Long> cityIds) {}
}
