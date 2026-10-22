package com.guens.fieldcollect.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password,
                           String deviceId, String deviceModel) {}
