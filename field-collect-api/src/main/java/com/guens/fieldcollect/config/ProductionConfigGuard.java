package com.guens.fieldcollect.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Refuse de démarrer en production avec les valeurs de développement ou des secrets trop faibles. */
@Component
@Profile("prod")
public class ProductionConfigGuard {

    private final String jwtSecret;
    private final String aesKey;
    private final String pepper;
    private final String adminPassword;
    private final String minioAccessKey;
    private final String minioSecretKey;

    public ProductionConfigGuard(@Value("${app.jwt.secret}") String jwtSecret,
                                 @Value("${app.crypto.aes-key-base64}") String aesKey,
                                 @Value("${app.crypto.hash-pepper}") String pepper,
                                 @Value("${app.bootstrap-admin.password}") String adminPassword,
                                 @Value("${app.storage.minio.access-key}") String minioAccessKey,
                                 @Value("${app.storage.minio.secret-key}") String minioSecretKey) {
        this.jwtSecret = jwtSecret;
        this.aesKey = aesKey;
        this.pepper = pepper;
        this.adminPassword = adminPassword;
        this.minioAccessKey = minioAccessKey;
        this.minioSecretKey = minioSecretKey;
    }

    @PostConstruct
    void check() {
        require(jwtSecret.getBytes(StandardCharsets.UTF_8).length >= 32 && !jwtSecret.startsWith("change-me"),
                "JWT_SECRET : 32 caractères minimum et différent de la valeur de développement");
        require(!aesKey.equals("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="),
                "AES_KEY_BASE64 : la clé de développement est interdite");
        require(pepper.length() >= 16 && !pepper.equals("dev-pepper"),
                "HASH_PEPPER : 16 caractères minimum et différent de la valeur de développement");
        require(adminPassword.length() >= 12 && !adminPassword.equals("Admin#12345"),
                "ADMIN_PASSWORD : 12 caractères minimum et différent de la valeur de développement");
        require(!minioAccessKey.isBlank() && minioSecretKey.length() >= 8 && !minioAccessKey.startsWith("change-me"),
                "MINIO_ACCESS_KEY / MINIO_SECRET_KEY : requis (secret 8 caractères minimum, pas la valeur d'exemple)");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("Configuration de production invalide — " + message);
    }
}
