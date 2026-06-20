package com.airportbus.user.security;

import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @AfterEach
    void cleanup() { CurrentUser.clear(); }

    @Test
    void requireAdmin_allowsSuperAdmin() {
        CurrentUser.set(new JwtPrincipal(1L, "SUPER_ADMIN"));
        assertThat(CurrentUser.requireAdmin().role()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void requireAdmin_allowsOperator() {
        CurrentUser.set(new JwtPrincipal(2L, "OPERATOR"));
        assertThat(CurrentUser.requireAdmin().userId()).isEqualTo(2L);
    }

    @Test
    void requireAdmin_rejectsRegularUser_withForbidden() {
        CurrentUser.set(new JwtPrincipal(3L, "USER"));
        assertThatThrownBy(CurrentUser::requireAdmin)
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }

    @Test
    void requireAdmin_rejectsAnonymous_withUnauthorized() {
        assertThatThrownBy(CurrentUser::requireAdmin)
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
