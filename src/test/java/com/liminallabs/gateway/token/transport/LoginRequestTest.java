package com.liminallabs.gateway.token.transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoginRequestTest {

    @Test
    void roundTripsThroughCookieValue() {
        LoginRequest login = LoginRequest.start("/campaigns?page=2&q=a b");

        LoginRequest decoded = LoginRequest.decode(login.encode()).orElseThrow();

        assertThat(decoded).isEqualTo(login);
        assertThat(login.encode()).matches("[A-Za-z0-9_.-]+");
    }

    @Test
    void unsafeReturnToIsDropped() {
        LoginRequest login = LoginRequest.start("//evil.com");

        assertThat(login.returnTo()).isNull();
        assertThat(LoginRequest.decode(login.encode()).orElseThrow().returnTo()).isNull();
    }

    @Test
    void eachLoginGetsFreshStateAndPkce() {
        LoginRequest a = LoginRequest.start(null);
        LoginRequest b = LoginRequest.start(null);

        assertThat(a.state()).isNotEqualTo(b.state());
        assertThat(a.pkce().challenge()).isNotEqualTo(b.pkce().challenge());
    }

    @Test
    void stateComparison() {
        LoginRequest login = LoginRequest.start(null);

        assertThat(login.matchesState(login.state())).isTrue();
        assertThat(login.matchesState("other")).isFalse();
        assertThat(login.matchesState(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "a.b", "a..", ".b.", "a.b.%%%"})
    void malformedCookieIsRejected(String value) {
        assertThat(LoginRequest.decode(value)).isEmpty();
    }
}
