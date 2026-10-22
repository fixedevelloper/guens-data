package com.guens.fieldcollect.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class UserDtos {
    private UserDtos() {}

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "caractères non autorisés") String username,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 30) String phone,
            Role role) {}

    public record UpdateUserRequest(@NotBlank @Size(max = 150) String fullName, @Size(max = 30) String phone) {}

    public record StatusRequest(@NotNull Boolean active) {}

    public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 100) String newPassword) {}

    public record AssignmentItem(@NotNull Long applicationId, @NotNull Long cityId) {}

    public record AssignmentsRequest(@NotNull List<@Valid AssignmentItem> assignments) {}

    public record AssignmentResponse(Long applicationId, String applicationName, Long cityId, String cityName) {}

    public record UserResponse(Long id, String username, String fullName, String phone, Role role, boolean active,
                               String deviceId, String deviceModel, Instant deviceBoundAt, Instant lastLoginAt,
                               Instant lockedUntil, List<AssignmentResponse> assignments) {}
}
