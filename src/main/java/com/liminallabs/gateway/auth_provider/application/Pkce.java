package com.liminallabs.gateway.auth_provider.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Par PKCE (RFC 7636) com método S256. */
public record Pkce(String verifier, String challenge) {

    public static final String METHOD = "S256";

    private static final SecureRandom RANDOM = new SecureRandom();

    public static Pkce generate() {
        String verifier = randomUrlSafe(32);
        return new Pkce(verifier, challengeOf(verifier));
    }

    public static String challengeOf(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return base64Url(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /** Valor aleatório em base64url sem padding, também usado para o {@code state}. */
    public static String randomUrlSafe(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return base64Url(buffer);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
