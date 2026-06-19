package com.airportbus.user.mail;

public interface Mailer {
    void send(String to, String subject, String body);
}
