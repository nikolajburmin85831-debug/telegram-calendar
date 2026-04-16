package io.github.nadya.assistant.adapter.out.persistence.memory;

import io.github.nadya.assistant.ports.out.AuditEntry;
import io.github.nadya.assistant.ports.out.AuditPort;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryAuditAdapter implements AuditPort {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void record(AuditEntry entry) {
        entries.add(entry);
    }

    public List<AuditEntry> entries() {
        return List.copyOf(entries);
    }
}
