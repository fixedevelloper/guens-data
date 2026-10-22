package com.guens.fieldcollect.security;

import com.guens.fieldcollect.config.ProductionConfigGuard;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ProductionConfigGuardTest {

    private static final String STRONG_JWT = "k8Jd93hsLq02nZx7TwPv5RbYc1AeGu4M-prod";
    private static final String STRONG_AES = "q2c0n3Jr0o1m5ZV3o2bWJ0cy1Cwq9hX0Zq1o3gQmQ8E=";

    private void check(String jwt, String aes, String pepper, String admin) throws Exception {
        ProductionConfigGuard g = new ProductionConfigGuard(jwt, aes, pepper, admin, "minio-prod-key", "minio-prod-secret");
        Method m = ProductionConfigGuard.class.getDeclaredMethod("check");
        m.setAccessible(true);
        try {
            m.invoke(g);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void acceptsStrongSecrets() {
        assertDoesNotThrow(() -> check(STRONG_JWT, STRONG_AES, "pepper-prod-0123456789", "Str0ng-Admin-Pass!"));
    }

    @Test
    void rejectsMissingMinioCredentials() {
        ProductionConfigGuard g = new ProductionConfigGuard(STRONG_JWT, STRONG_AES, "pepper-prod-0123456789",
                "Str0ng-Admin-Pass!", "", "short");
        assertThrows(Exception.class, () -> {
            Method m = ProductionConfigGuard.class.getDeclaredMethod("check");
            m.setAccessible(true);
            m.invoke(g);
        });
    }

    @Test
    void rejectsDevelopmentDefaults() {
        String devAes = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
        assertThrows(IllegalStateException.class,
                () -> check("change-me-change-me-change-me-change-me-32b", STRONG_AES, "pepper-prod-0123456789", "Str0ng-Admin-Pass!"));
        assertThrows(IllegalStateException.class,
                () -> check(STRONG_JWT, devAes, "pepper-prod-0123456789", "Str0ng-Admin-Pass!"));
        assertThrows(IllegalStateException.class,
                () -> check(STRONG_JWT, STRONG_AES, "dev-pepper", "Str0ng-Admin-Pass!"));
        assertThrows(IllegalStateException.class,
                () -> check(STRONG_JWT, STRONG_AES, "pepper-prod-0123456789", "Admin#12345"));
        assertThrows(IllegalStateException.class,
                () -> check("short", STRONG_AES, "pepper-prod-0123456789", "Str0ng-Admin-Pass!"));
    }
}
