package com.guens.fieldcollect.geo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class GeoDtos {
    private GeoDtos() {}

    public record CountryRequest(@NotBlank @Size(min = 2, max = 2) String isoCode,
                                 @NotBlank @Size(max = 100) String name,
                                 Boolean active) {}

    public record CountryResponse(Long id, String isoCode, String name, boolean active) {}

    public record CityRequest(@NotNull Long countryId,
                              @NotBlank @Size(max = 100) String name,
                              Boolean active) {}

    public record CityResponse(Long id, Long countryId, String countryName, String name, boolean active) {}
}
