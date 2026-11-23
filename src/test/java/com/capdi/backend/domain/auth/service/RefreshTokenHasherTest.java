package com.capdi.backend.domain.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenHasherTest {

    @Test
    void sha256HexMatchesKnownVectorInLowercaseHex() {
        assertThat(RefreshTokenHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void newRawTokenIs43CharBase64UrlWithoutPadding() {
        String token = RefreshTokenHasher.newRawToken();
        assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void newRawTokenIsDifferentEachCall() {
        assertThat(RefreshTokenHasher.newRawToken()).isNotEqualTo(RefreshTokenHasher.newRawToken());
    }
}
