package com.airportbus.user.mail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SmtpMailerTest {

    @Test
    void setsFromToAndSends() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpMailer mailer = new SmtpMailer(sender, "fpfos@hotmail.com");

        mailer.send("user@x.com", "验证码", "码:123456");

        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(cap.capture());
        SimpleMailMessage m = cap.getValue();
        assertThat(m.getFrom()).isEqualTo("fpfos@hotmail.com");
        assertThat(m.getTo()).containsExactly("user@x.com");
        assertThat(m.getSubject()).isEqualTo("验证码");
        assertThat(m.getText()).contains("123456");
    }

    @Test
    void blankFromOmitted() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpMailer mailer = new SmtpMailer(sender, "");

        mailer.send("user@x.com", "s", "b");

        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(cap.capture());
        assertThat(cap.getValue().getFrom()).isNull();
    }
}
