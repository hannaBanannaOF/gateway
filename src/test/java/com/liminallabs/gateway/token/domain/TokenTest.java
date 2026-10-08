package com.liminallabs.gateway.token.domain;

import static com.liminallabs.gateway.support.Tokens.NOW;
import static com.liminallabs.gateway.support.Tokens.token;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenTest {

    @Test
    void freshTokenIsValid() {
        Token token = token("a", "r", 300, 1800, 0);

        assertThat(token.isAccessTokenValid(NOW)).isTrue();
        assertThat(token.isRefreshTokenValid(NOW)).isTrue();
        assertThat(token.isExpired(NOW)).isFalse();
    }

    @Test
    void accessExpiresBeforeRefresh() {
        Token token = token("a", "r", 300, 1800, 600);

        assertThat(token.isAccessTokenValid(NOW)).isFalse();
        assertThat(token.isRefreshTokenValid(NOW)).isTrue();
        assertThat(token.isExpired(NOW)).isFalse();
    }

    @Test
    void bothExpired() {
        Token token = token("a", "r", 300, 1800, 3600);

        assertThat(token.isExpired(NOW)).isTrue();
    }

    @Test
    void sessionExpiresWithLongestLifetime() {
        assertThat(token("a", "r", 3600, 1800, 0).sessionExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(token("a", "r", 300, 1800, 0).sessionExpiresAt()).isEqualTo(NOW.plusSeconds(1800));
    }

    @Test
    void tokenWithoutLifetimeNeverExpires() {
        // Ex.: offline token do Keycloak (refresh_expires_in = 0) e access sem prazo
        Token token = token("a", "r", 0, 0, 999_999);

        assertThat(token.sessionExpiresAt()).isNull();
        assertThat(token.isExpired(NOW)).isFalse();
    }

    @Test
    void refreshTokenFallbackKeepsPreviousOnlyWhenMissing() {
        assertThat(token("a", null, 300, 1800, 0).withRefreshTokenFallback("old").refreshToken()).isEqualTo("old");
        assertThat(token("a", "", 300, 1800, 0).withRefreshTokenFallback("old").refreshToken()).isEqualTo("old");
        assertThat(token("a", "new", 300, 1800, 0).withRefreshTokenFallback("old").refreshToken()).isEqualTo("new");
    }
}
