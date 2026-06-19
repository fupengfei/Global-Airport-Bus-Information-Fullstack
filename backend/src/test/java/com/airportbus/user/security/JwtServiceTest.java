package com.airportbus.user.security;

import com.airportbus.common.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    private final JwtService jwt = new JwtService(
            "test-secret-test-secret-test-secret-0123456789", 900);

    @Test
    void roundTrip() {
        String t = jwt.issueAccess(42L, "USER");
        JwtPrincipal p = jwt.parse(t);
        assertThat(p.userId()).isEqualTo(42L);
        assertThat(p.role()).isEqualTo("USER");
    }

    @Test
    void tamperedTokenRejected() {
        String t = jwt.issueAccess(1L, "USER");
        assertThatThrownBy(() -> jwt.parse(t + "x")).isInstanceOf(ApiException.class);
    }

    @Test
    void expiredTokenRejected() {
        JwtService shortLived = new JwtService(
                "test-secret-test-secret-test-secret-0123456789", -1);
        String t = shortLived.issueAccess(1L, "USER");
        assertThatThrownBy(() -> shortLived.parse(t)).isInstanceOf(ApiException.class);
    }
}
