package com.guens.fieldcollect.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "login_attempts")
@Getter @Setter @NoArgsConstructor
public class LoginAttempt {
    public enum Reason { OK, BAD_CREDENTIALS, DEVICE_MISMATCH, INACTIVE, LOCKED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String username;
    @Column(name = "user_id") private Long userId;
    @Column(name = "device_id") private String deviceId;
    @Column(nullable = false) private boolean success;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Reason reason;
    @Column(name = "ip_address") private String ipAddress;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
}
