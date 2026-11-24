package com.example.httpreading.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void issuedTokenUsesConfiguredLifetime() {
        JwtService service = new JwtService("test-secret-with-at-least-32-characters", 120);

        Instant before = Instant.now().plusSeconds(119);
        JwtService.IssuedToken issued = service.issueToken(7L, "reader");
        Instant after = Instant.now().plusSeconds(121);

        assertThat(issued.expiresAt()).isBetween(before, after);
        assertThat(service.getUserId(issued.token())).isEqualTo(7L);
        assertThat(Duration.between(Instant.now(), issued.expiresAt()).toSeconds())
                .isBetween(118L, 120L);
    }
}
