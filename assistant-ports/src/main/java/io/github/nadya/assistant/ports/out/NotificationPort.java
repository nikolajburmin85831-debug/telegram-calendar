package io.github.nadya.assistant.ports.out;

public interface NotificationPort {

    void send(NotificationCommand command);
}
