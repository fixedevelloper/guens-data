package com.guens.fieldcollect.auth;

import com.guens.fieldcollect.auth.LoginAttempt.Reason;
import com.guens.fieldcollect.auth.dto.LoginRequest;
import com.guens.fieldcollect.auth.dto.RefreshRequest;
import com.guens.fieldcollect.auth.dto.TokenResponse;
import com.guens.fieldcollect.common.exception.ApiException;
import com.guens.fieldcollect.security.JwtService;
import com.guens.fieldcollect.user.Role;
import com.guens.fieldcollect.user.User;
import com.guens.fieldcollect.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final LoginAttemptRepository attempts;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Duration refreshTtl;
    private final int maxFailedAttempts;
    private final Duration lockDuration;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, LoginAttemptRepository attempts,
                       PasswordEncoder encoder, JwtService jwt,
                       @Value("${app.jwt.refresh-ttl-days}") long refreshTtlDays,
                       @Value("${app.security.max-failed-attempts}") int maxFailedAttempts,
                       @Value("${app.security.lock-minutes}") long lockMinutes) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.attempts = attempts;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    // noRollbackFor : on veut conserver la trace des tentatives échouées
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(LoginRequest req, String ip) {
        User user = users.findByUsername(req.username()).orElse(null);
        Instant now = Instant.now();
        if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            record(req, user, false, Reason.LOCKED, ip);
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED",
                    "Compte temporairement verrouillé suite à trop d'échecs. Réessayez plus tard.");
        }
        if (user == null || !encoder.matches(req.password(), user.getPasswordHash())) {
            if (user != null) registerFailure(user, now);
            record(req, user, false, Reason.BAD_CREDENTIALS, ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Identifiants invalides.");
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (!user.isActive()) {
            record(req, user, false, Reason.INACTIVE, ip);
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Compte désactivé.");
        }
        if (user.getRole() == Role.AGENT) {
            user = enforceDevice(req, user, ip);
        }
        user.setLastLoginAt(Instant.now());
        record(req, user, true, Reason.OK, ip);
        return issue(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest req) {
        RefreshToken stored = refreshTokens.findByTokenHash(sha256(req.refreshToken()))
                .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                        "Session expirée, reconnectez-vous."));
        User user = users.findById(stored.getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                        "Session expirée, reconnectez-vous."));
        if (user.getRole() == Role.AGENT
                && (user.getDeviceId() == null || !user.getDeviceId().equals(req.deviceId()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEVICE_MISMATCH",
                    "Cet appareil n'est pas autorisé pour ce compte.");
        }
        stored.setRevoked(true); // rotation
        return issue(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(sha256(refreshToken)).ifPresent(t -> t.setRevoked(true));
    }

    private void registerFailure(User user, Instant now) {
        int failures = user.getFailedLoginAttempts() + 1;
        if (failures >= maxFailedAttempts) {
            user.setLockedUntil(now.plus(lockDuration));
            failures = 0;
        }
        user.setFailedLoginAttempts(failures);
    }

    private User enforceDevice(LoginRequest req, User user, String ip) {
        String deviceId = req.deviceId();
        if (deviceId == null || deviceId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_ID_REQUIRED",
                    "L'identifiant de l'appareil est requis.");
        }
        if (user.getDeviceId() == null) {
            // Premier login : liaison de l'appareil, refusée s'il appartient déjà à un autre compte.
            if (users.findByDeviceId(deviceId).isPresent()) {
                record(req, user, false, Reason.DEVICE_MISMATCH, ip);
                throw new ApiException(HttpStatus.FORBIDDEN, "DEVICE_ALREADY_USED",
                        "Cet appareil est déjà lié à un autre compte.");
            }
            users.bindDevice(user.getId(), deviceId, req.deviceModel(), Instant.now());
            user = users.findById(user.getId()).orElseThrow();
        }
        if (!deviceId.equals(user.getDeviceId())) {
            record(req, user, false, Reason.DEVICE_MISMATCH, ip);
            throw new ApiException(HttpStatus.FORBIDDEN, "DEVICE_MISMATCH",
                    "Ce compte est lié à un autre appareil.");
        }
        return user;
    }

    private TokenResponse issue(User user) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(sha256(refresh));
        rt.setDeviceId(user.getDeviceId());
        rt.setExpiresAt(Instant.now().plus(refreshTtl));
        refreshTokens.save(rt);

        return new TokenResponse(jwt.generateAccessToken(user), refresh, jwt.accessTtl().toSeconds(),
                new TokenResponse.UserInfo(user.getId(), user.getUsername(), user.getFullName(),
                        user.getRole().name()));
    }

    private void record(LoginRequest req, User user, boolean success, Reason reason, String ip) {
        LoginAttempt a = new LoginAttempt();
        a.setUsername(req.username());
        a.setUserId(user == null ? null : user.getId());
        a.setDeviceId(req.deviceId());
        a.setSuccess(success);
        a.setReason(reason);
        a.setIpAddress(ip);
        attempts.save(a);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
