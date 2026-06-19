package com.airportbus.user.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 配置了 spring.mail.host 时启用:走真实 SMTP。 */
@Component
@ConditionalOnProperty(name = "spring.mail.host")
public class SmtpMailer implements Mailer {
    private final JavaMailSender sender;
    public SmtpMailer(JavaMailSender sender) { this.sender = sender; }
    @Override public void send(String to, String subject, String body) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setTo(to); m.setSubject(subject); m.setText(body);
        sender.send(m);
    }
}
