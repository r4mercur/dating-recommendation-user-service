package com.bjarne.datingrecommendationsuserservice.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigurationTest {

    private final SecurityConfiguration configuration = new SecurityConfiguration();

    @Test
    void missingJwtSecretPreventsTheStart() {
        assertThatThrownBy(() -> configuration.jwtSecretKey(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not set")
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void tooShortJwtSecretPreventsTheStart() {
        assertThatThrownBy(() -> configuration.jwtSecretKey("short-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void secretWithAtLeast32BytesIsAccepted() {
        assertThat(configuration.jwtSecretKey("a".repeat(32)).getAlgorithm()).isEqualTo("HmacSHA256");
    }
}
