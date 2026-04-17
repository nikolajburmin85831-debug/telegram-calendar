package io.github.nadya.assistant.app.ec2.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant.persistence")
public record AssistantPersistenceProperties(
        StorageMode mode,
        StorageMode auditMode
) {

    public AssistantPersistenceProperties {
        mode = mode == null ? StorageMode.INMEMORY : mode;
        auditMode = auditMode == null ? StorageMode.INMEMORY : auditMode;
    }

    public boolean usesJdbcState() {
        return mode == StorageMode.JDBC;
    }

    public boolean usesJdbcAudit() {
        return auditMode == StorageMode.JDBC;
    }

    public boolean usesJdbcStorage() {
        return usesJdbcState() || usesJdbcAudit();
    }
}
