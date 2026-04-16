package io.github.nadya.assistant.ports.out;

public interface AuditPort {

    void record(AuditEntry entry);
}
