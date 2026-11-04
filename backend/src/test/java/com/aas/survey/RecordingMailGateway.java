package com.aas.survey;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** The mail stub for integration tests: remembers what would have been sent. */
@Component
@Primary
public class RecordingMailGateway implements MailGateway {

    public record Mail(String to, String subject, String body) {
    }

    private final List<Mail> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failNext;

    public void failNextSend() {
        failNext = true;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("Mail server unavailable");
        }
        sent.add(new Mail(to, subject, body));
    }

    public List<Mail> to(String address) {
        return sent.stream().filter(m -> m.to().equals(address)).toList();
    }
}
