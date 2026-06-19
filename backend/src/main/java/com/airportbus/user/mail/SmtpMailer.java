package com.airportbus.user.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 配置了 spring.mail.host 时启用:走真实 SMTP。
 *  发件人取 airportbus.mail.from(缺省回落到 spring.mail.username)——
 *  Outlook/QQ/163 等要求 From 必须等于认证邮箱,否则拒发。 */
@Component
@ConditionalOnProperty(name = "spring.mail.host")
public class SmtpMailer implements Mailer {
    private final JavaMailSender sender;
    private final String from;

    public SmtpMailer(JavaMailSender sender,
                      @Value("${airportbus.mail.from:${spring.mail.username:}}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override public void send(String to, String subject, String body) {
        SimpleMailMessage m = new SimpleMailMessage();
        if (from != null && !from.isBlank()) m.setFrom(from);
        m.setTo(to); m.setSubject(subject); m.setText(body);
        sender.send(m);
    }
}
