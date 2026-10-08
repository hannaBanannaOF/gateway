package com.liminallabs.gateway.auth_provider.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PkceTest {

    @Test
    void challengeMatchesRfc7636Example() {
        // RFC 7636, apêndice B
        assertThat(Pkce.challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
            .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void generatedPairsAreRandomAndConsistent() {
        Pkce a = Pkce.generate();
        Pkce b = Pkce.generate();

        assertThat(a.verifier()).isNotEqualTo(b.verifier()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(a.challenge()).isEqualTo(Pkce.challengeOf(a.verifier()));
    }
}
