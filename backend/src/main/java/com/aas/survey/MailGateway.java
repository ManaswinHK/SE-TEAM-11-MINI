package com.aas.survey;

/** The Email Gateway from the SAD: the only place the application hands mail to the outside world. */
public interface MailGateway {

    void send(String to, String subject, String body);
}
