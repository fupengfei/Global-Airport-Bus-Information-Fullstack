package com.airportbus.user.service;

import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import com.airportbus.user.api.dto.AuthDtos.*;
import com.airportbus.user.mail.Mailer;
import com.airportbus.user.mapper.RefreshTokenMapper;
import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.model.AppUser;
import com.airportbus.user.security.JwtPrincipal;
import com.airportbus.user.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserMapper users;
    private final RefreshTokenMapper tokens;
    private final AuthCacheService cache;
    private final JwtService jwt;
    private final PasswordEncoder encoder;
    private final Mailer mailer;
    private final long refreshTtlDays;
    private final String appBaseUrl;
    private final SecureRandom rnd = new SecureRandom();

    public AuthService(UserMapper users, RefreshTokenMapper tokens, AuthCacheService cache,
                       JwtService jwt, PasswordEncoder encoder, Mailer mailer,
                       @Value("${airportbus.jwt.refresh-ttl-days:14}") long refreshTtlDays,
                       @Value("${airportbus.app.base-url:http://localhost:5173}") String appBaseUrl) {
        this.users = users; this.tokens = tokens; this.cache = cache;
        this.jwt = jwt; this.encoder = encoder; this.mailer = mailer;
        this.refreshTtlDays = refreshTtlDays; this.appBaseUrl = appBaseUrl;
    }

    public JwtPrincipal parseAccess(String accessToken) { return jwt.parse(accessToken); }

    // ── 注册验证码 ──
    public Sent sendRegisterCode(SendCodeReq req) {
        String email = req.email() == null ? "" : req.email().trim();
        if (!EMAIL.matcher(email).matches()) throw new ApiException(ErrorCode.INVALID_INPUT, "bad email");
        if (users.existsByEmail(email)) throw new ApiException(ErrorCode.EMAIL_TAKEN, "email taken");
        if (!cache.canSendRegisterCode(email)) throw new ApiException(ErrorCode.RATE_LIMITED, "try later");
        String code = cache.issueRegisterCode(email);
        mailer.send(email, "你的注册验证码", "验证码:" + code + "(10 分钟内有效)");
        return new Sent(true);
    }

    // ── 注册 ──
    @Transactional
    public TokenPair register(RegisterReq req) {
        String username = req.username() == null ? "" : req.username().trim();
        String email = req.email() == null ? "" : req.email().trim();
        if (username.length() < 3 || username.length() > 20) throw new ApiException(ErrorCode.INVALID_INPUT, "username 3-20");
        if (req.password() == null || req.password().length() < 8) throw new ApiException(ErrorCode.INVALID_INPUT, "password >= 8");
        if (!cache.verifyAndConsumeRegisterCode(email, req.code())) throw new ApiException(ErrorCode.INVALID_CODE, "bad code");
        if (users.existsByUsername(username)) throw new ApiException(ErrorCode.USERNAME_TAKEN, "username taken");
        if (users.existsByEmail(email)) throw new ApiException(ErrorCode.EMAIL_TAKEN, "email taken");
        AppUser u = new AppUser();
        u.username = username; u.email = email; u.passwordHash = encoder.encode(req.password());
        u.locale = "zh-CN"; u.role = "USER"; u.emailVerified = true;
        users.insertUser(u);
        return issueTokens(u.id, u.role);
    }

    // ── 登录 ──
    public TokenPair login(LoginReq req) {
        String account = req.account() == null ? "" : req.account().trim();
        if (cache.isLoginLocked(account)) throw new ApiException(ErrorCode.RATE_LIMITED, "too many attempts");
        AppUser u = users.findByAccount(account);
        if (u == null || !encoder.matches(req.password() == null ? "" : req.password(), u.passwordHash)) {
            cache.recordLoginFail(account);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "bad credentials");
        }
        cache.clearLoginFail(account);
        return issueTokens(u.id, u.role);
    }

    // ── 刷新(轮换)──
    @Transactional
    public TokenPair refresh(RefreshReq req) {
        String raw = req.refreshToken();
        RefreshTokenMapper.Row row = raw == null ? null : tokens.findByHash(sha256(raw));
        if (row == null || row.revoked() || row.expiresAt().isBefore(LocalDateTime.now()))
            throw new ApiException(ErrorCode.INVALID_TOKEN, "invalid refresh");
        tokens.revokeByHash(sha256(raw));
        AppUser u = users.findById(row.userId());
        if (u == null) throw new ApiException(ErrorCode.INVALID_TOKEN, "user gone");
        return issueTokens(u.id, u.role);
    }

    public Ok logout(RefreshReq req) {
        if (req.refreshToken() != null) tokens.revokeByHash(sha256(req.refreshToken()));
        return new Ok(true);
    }

    // ── 找回密码 ──
    public Sent forgot(ForgotReq req) {
        String email = req.email() == null ? "" : req.email().trim();
        AppUser u = users.findByEmail(email);
        if (u != null) {
            String token = cache.issueResetToken(u.id);
            mailer.send(email, "重置你的密码",
                    "点此重置(30 分钟内有效):" + appBaseUrl + "/reset-password?token=" + token);
        }
        return new Sent(true); // 不泄露邮箱是否注册
    }

    @Transactional
    public Ok reset(ResetReq req) {
        if (req.newPassword() == null || req.newPassword().length() < 8)
            throw new ApiException(ErrorCode.INVALID_INPUT, "password >= 8");
        Long uid = cache.consumeResetToken(req.token());
        if (uid == null) throw new ApiException(ErrorCode.INVALID_TOKEN, "invalid reset token");
        users.updatePassword(uid, encoder.encode(req.newPassword()));
        tokens.revokeAllForUser(uid);
        return new Ok(true);
    }

    // ── 个人中心 ──
    public MeView me(long userId) {
        AppUser u = users.findById(userId);
        if (u == null) throw new ApiException(ErrorCode.UNAUTHORIZED, "no user");
        return new MeView(u.username, u.email, u.locale, u.role);
    }
    public MeView updateMe(long userId, UpdateMeReq req) {
        if (req.locale() != null) users.updateLocale(userId, req.locale());
        return me(userId);
    }
    @Transactional
    public Ok changePassword(long userId, ChangePasswordReq req) {
        AppUser u = users.findById(userId);
        if (u == null) throw new ApiException(ErrorCode.UNAUTHORIZED, "no user");
        if (!encoder.matches(req.oldPassword() == null ? "" : req.oldPassword(), u.passwordHash))
            throw new ApiException(ErrorCode.INVALID_INPUT, "old password wrong");
        if (req.newPassword() == null || req.newPassword().length() < 8)
            throw new ApiException(ErrorCode.INVALID_INPUT, "password >= 8");
        users.updatePassword(userId, encoder.encode(req.newPassword()));
        tokens.revokeAllForUser(userId);
        return new Ok(true);
    }

    // ── 内部 ──
    private TokenPair issueTokens(long userId, String role) {
        String access = jwt.issueAccess(userId, role);
        byte[] b = new byte[32]; rnd.nextBytes(b);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        tokens.insert(userId, sha256(refresh), LocalDateTime.now().plusDays(refreshTtlDays));
        return new TokenPair(access, refresh);
    }
    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
