package com.airportbus.user.service;

import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import com.airportbus.user.api.dto.AuthDtos.SendCodeReq;
import com.airportbus.user.mail.Mailer;
import com.airportbus.user.mapper.RefreshTokenMapper;
import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 纯单测:邮件发送失败应返回明确的 MAIL_SEND_FAILED,而非笼统 500。 */
class AuthServiceMailFailureTest {

    @Test
    void mailFailureMapsToMailSendFailed() {
        UserMapper users = mock(UserMapper.class);
        RefreshTokenMapper tokens = mock(RefreshTokenMapper.class);
        AuthCacheService cache = mock(AuthCacheService.class);
        JwtService jwt = mock(JwtService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Mailer mailer = mock(Mailer.class);

        when(users.existsByEmail("a@b.com")).thenReturn(false);
        when(cache.canSendRegisterCode("a@b.com")).thenReturn(true);
        when(cache.issueRegisterCode("a@b.com")).thenReturn("123456");
        doThrow(new MailSendException("smtp down")).when(mailer).send(any(), any(), any());

        AuthService auth = new AuthService(users, tokens, cache, jwt, encoder, mailer, 14, "http://x");

        assertThatThrownBy(() -> auth.sendRegisterCode(new SendCodeReq("a@b.com")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code)
                .isEqualTo(ErrorCode.MAIL_SEND_FAILED);
    }
}
