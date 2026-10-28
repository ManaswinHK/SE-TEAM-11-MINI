package com.aas.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

    /** RFC 6238 Appendix B test vector (SHA1, T = 59 s): 94287082, i.e. 287082 with 6 digits. */
    @Test
    void matchesRfc6238TestVector() {
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(TotpService.code(key, 59 / 30, 8)).isEqualTo("94287082");
        assertThat(TotpService.code(key, 59 / 30, 6)).isEqualTo("287082");
    }

    @Test
    void base32RoundTrips() {
        byte[] data = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        String encoded = TotpService.base32Encode(data);
        assertThat(encoded).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(TotpService.base32Decode(encoded)).isEqualTo(data);
    }

    @Test
    void acceptsCurrentAndAdjacentStepOnly() {
        Instant t = Instant.parse("2026-10-28T10:00:00Z");
        TotpService now = new TotpService(Clock.fixed(t, ZoneOffset.UTC));
        String secret = now.newSecret();
        String code = now.currentCode(secret);

        assertThat(now.verify(secret, code)).isTrue();
        assertThat(new TotpService(Clock.fixed(t.plusSeconds(30), ZoneOffset.UTC)).verify(secret, code)).isTrue();
        assertThat(new TotpService(Clock.fixed(t.plusSeconds(120), ZoneOffset.UTC)).verify(secret, code)).isFalse();
        assertThat(now.verify(secret, "12345")).isFalse();
        assertThat(now.verify(secret, null)).isFalse();
    }
}
