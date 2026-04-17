package io.github.nadya.assistant.adapter.out.persistence.jdbc;

import java.time.Duration;

public record JdbcPersistenceSettings(String schema, Duration idempotencyTtl) {

    public JdbcPersistenceSettings {
        schema = schema == null || schema.isBlank() ? "public" : schema.trim();
        idempotencyTtl = idempotencyTtl == null || idempotencyTtl.isNegative()
                ? Duration.ofHours(24)
                : idempotencyTtl;
    }
}
