package com.airportbus.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 仅在请求带 Bearer 时解析主体放入 CurrentUser;无 token 不拦截(公开端点照常)。
 *  受保护端点用 CurrentUser.require() 触发 401。注册为 bean 见 WebSecurityConfig。 */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    public JwtAuthFilter(JwtService jwt) { this.jwt = jwt; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            try {
                CurrentUser.set(jwt.parse(h.substring(7)));
            } catch (Exception ignored) {
                // 无效 token 不在此处抛错;受保护端点会因 CurrentUser 为空返回 401
            }
        }
        try {
            chain.doFilter(req, res);
        } finally {
            CurrentUser.clear();
        }
    }
}
