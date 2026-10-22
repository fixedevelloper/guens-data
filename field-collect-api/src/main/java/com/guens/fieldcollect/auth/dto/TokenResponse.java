package com.guens.fieldcollect.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds, UserInfo user) {
    public record UserInfo(Long id, String username, String fullName, String role) {}
}
