package com.guens.fieldcollect.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken, String deviceId) {}
