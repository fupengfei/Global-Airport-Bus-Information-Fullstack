package com.airportbus.user.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/** Redis 支撑:注册验证码、找回密码重置令牌、登录失败限流(均带 TTL)。 */
@Service
public class AuthCacheService {

    private static final Duration CODE_TTL = Duration.ofSeconds(600);
    private static final Duration RESEND_TTL = Duration.ofSeconds(60);
    private static final Duration RESET_TTL = Duration.ofSeconds(1800);
    private static final Duration LOGINFAIL_TTL = Duration.ofSeconds(900);
    private static final int LOGIN_MAX_FAILS = 10;

    private final StringRedisTemplate redis;
    private final SecureRandom rnd = new SecureRandom();

    public AuthCacheService(StringRedisTemplate redis) { this.redis = redis; }

    // ── 注册验证码 ──
    public boolean canSendRegisterCode(String email) {
        return !Boolean.TRUE.equals(redis.hasKey("evcode:rl:" + email));
    }
    public String issueRegisterCode(String email) {
        String code = String.format("%06d", rnd.nextInt(1_000_000));
        redis.opsForValue().set("evcode:" + email, code, CODE_TTL);
        redis.opsForValue().set("evcode:rl:" + email, "1", RESEND_TTL);
        return code;
    }
    public boolean verifyAndConsumeRegisterCode(String email, String code) {
        String key = "evcode:" + email;
        String stored = redis.opsForValue().get(key);
        if (stored != null && stored.equals(code)) {
            redis.delete(key);
            return true;
        }
        return false;
    }

    // ── 重置令牌 ──
    public String issueResetToken(long userId) {
        byte[] b = new byte[24];
        rnd.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        redis.opsForValue().set("pwreset:" + token, Long.toString(userId), RESET_TTL);
        return token;
    }
    public Long consumeResetToken(String token) {
        String key = "pwreset:" + token;
        String v = redis.opsForValue().get(key);
        if (v == null) return null;
        redis.delete(key);
        return Long.parseLong(v);
    }

    // ── 登录限流 ──
    public void recordLoginFail(String account) {
        String key = "loginfail:" + account;
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1L) redis.expire(key, LOGINFAIL_TTL);
    }
    public boolean isLoginLocked(String account) {
        String v = redis.opsForValue().get("loginfail:" + account);
        return v != null && Integer.parseInt(v) >= LOGIN_MAX_FAILS;
    }
    public void clearLoginFail(String account) {
        redis.delete("loginfail:" + account);
    }
}
