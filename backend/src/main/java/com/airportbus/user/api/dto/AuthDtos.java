package com.airportbus.user.api.dto;

public class AuthDtos {
    public record SendCodeReq(String email) {}
    public record RegisterReq(String username, String email, String code, String password) {}
    public record LoginReq(String account, String password) {}
    public record RefreshReq(String refreshToken) {}
    public record ForgotReq(String email) {}
    public record ResetReq(String token, String newPassword) {}
    public record ChangePasswordReq(String oldPassword, String newPassword) {}
    public record UpdateMeReq(String locale) {}

    public record TokenPair(String accessToken, String refreshToken) {}
    public record MeView(String username, String email, String locale, String role) {}
    public record Ok(boolean ok) {}
    public record Sent(boolean sent) {}
}
