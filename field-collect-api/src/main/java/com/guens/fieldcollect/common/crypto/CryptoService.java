package com.guens.fieldcollect.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Chiffrement AES-256-GCM (IV aléatoire préfixé) et empreinte HMAC-SHA256 pour les recherches d'unicité. */
@Service
public class CryptoService {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;

    public CryptoService(@Value("${app.crypto.aes-key-base64}") String keyB64,
                         @Value("${app.crypto.hash-pepper}") String pepper) {
        byte[] key = Base64.getDecoder().decode(keyB64);
        if (key.length != 32) {
            throw new IllegalStateException("app.crypto.aes-key-base64 doit décoder 32 octets (AES-256).");
        }
        this.aesKey = new SecretKeySpec(key, "AES");
        this.hmacKey = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain);
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Échec du chiffrement", e);
        }
    }

    public byte[] decrypt(byte[] data) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, data, 0, IV_LEN));
            return c.doFinal(data, IV_LEN, data.length - IV_LEN);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Échec du déchiffrement", e);
        }
    }

    public byte[] encryptString(String s) {
        return encrypt(s.getBytes(StandardCharsets.UTF_8));
    }

    public String decryptString(byte[] data) {
        return new String(decrypt(data), StandardCharsets.UTF_8);
    }

    public String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
