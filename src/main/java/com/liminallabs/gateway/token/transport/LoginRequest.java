package com.liminallabs.gateway.token.transport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

import com.liminallabs.gateway.auth_provider.application.Pkce;

/**
 * Login em andamento, guardado no cookie de login entre {@code /oauth/login} e {@code /oauth/callback}.
 * Serializado como {@code state.verifier.returnTo} (cada parte em base64url, sem caracteres proibidos em cookie).
 */
record LoginRequest(String state, Pkce pkce, String returnTo) {

    static LoginRequest start(String returnTo) {
        return new LoginRequest(Pkce.randomUrlSafe(32), Pkce.generate(), ReturnPath.orNull(returnTo));
    }

    String encode() {
        String path = returnTo == null ? "" : Base64.getUrlEncoder().withoutPadding()
            .encodeToString(returnTo.getBytes(StandardCharsets.UTF_8));
        return state + "." + pkce.verifier() + "." + path;
    }

    static Optional<LoginRequest> decode(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return Optional.empty();
        }
        try {
            String returnTo = parts[2].isEmpty() ? null
                : new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
            Pkce pkce = new Pkce(parts[1], Pkce.challengeOf(parts[1]));
            // O cookie pode ter sido adulterado: revalida o caminho
            return Optional.of(new LoginRequest(parts[0], pkce, ReturnPath.orNull(returnTo)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Comparação em tempo constante para não vazar o state por timing. */
    boolean matchesState(String candidate) {
        return candidate != null && MessageDigest.isEqual(
            state.getBytes(StandardCharsets.US_ASCII), candidate.getBytes(StandardCharsets.US_ASCII));
    }
}
