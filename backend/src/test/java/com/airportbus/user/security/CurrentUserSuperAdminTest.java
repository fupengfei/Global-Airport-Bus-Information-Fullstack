package com.airportbus.user.security;

import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CurrentUserSuperAdminTest {
    @AfterEach void cleanup() { CurrentUser.clear(); }

    @Test void allowsSuperAdmin() {
        CurrentUser.set(new JwtPrincipal(1L, "SUPER_ADMIN"));
        assertThat(CurrentUser.requireSuperAdmin().role()).isEqualTo("SUPER_ADMIN");
    }
    @Test void rejectsOperator_withForbidden() {
        CurrentUser.set(new JwtPrincipal(2L, "OPERATOR"));
        assertThatThrownBy(CurrentUser::requireSuperAdmin)
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }
    @Test void rejectsAnonymous_withUnauthorized() {
        assertThatThrownBy(CurrentUser::requireSuperAdmin)
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
