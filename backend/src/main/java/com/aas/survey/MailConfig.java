package com.aas.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Sends through SMTP when spring.mail.host is set; otherwise mail stays in the log, which suits local development. */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    MailGateway mailGateway(ObjectProvider<JavaMailSender> sender, @Value("${aas.mail.from:no-reply@aas.local}") String from) {
        JavaMailSender smtp = sender.getIfAvailable();
        if (smtp == null) {
            log.warn("spring.mail.host is not set: emails will be written to the debug log and not delivered");
            return (to, subject, body) -> log.debug("Mail to {}: {}\n{}", to, subject, body);
        }
        return (to, subject, body) -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            smtp.send(message);
        };
    }
}
