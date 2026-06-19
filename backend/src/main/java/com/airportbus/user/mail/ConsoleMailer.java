package com.airportbus.user.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 默认邮件实现:不连 SMTP,把内容打印到控制台(dev)。配置 spring.mail.host 后让位给 SmtpMailer。 */
@Component
@ConditionalOnProperty(name = "spring.mail.host", havingValue = "__smtp__", matchIfMissing = true)
public class ConsoleMailer implements Mailer {
    private static final Logger log = LoggerFactory.getLogger(ConsoleMailer.class);
    @Override public void send(String to, String subject, String body) {
        log.info("\n==== DEV MAIL ====\nTO: {}\nSUBJECT: {}\n{}\n==================", to, subject, body);
    }
}
