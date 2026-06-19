package com.airportbus.user.security;

import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;

/** 请求范围的当前用户(由 JwtAuthFilter 填充,过滤器在 finally 清理)。 */
public final class CurrentUser {
    private static final ThreadLocal<JwtPrincipal> HOLDER = new ThreadLocal<>();
    private CurrentUser() {}
    public static void set(JwtPrincipal p) { HOLDER.set(p); }
    public static void clear() { HOLDER.remove(); }
    public static JwtPrincipal require() {
        JwtPrincipal p = HOLDER.get();
        if (p == null) throw new ApiException(ErrorCode.UNAUTHORIZED, "login required");
        return p;
    }
}
