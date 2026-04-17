package io.github.nadya.assistant.app.ec2.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant.persistence.jdbc")
public record AssistantPersistenceJdbcProperties(
        String url,
        String username,
        String password,
        String driverClassName,
        String schema,
        int poolMaxSize,
        int poolMinIdle,
        long connectionTimeoutMs
) {

    public AssistantPersistenceJdbcProperties {
        url = url == null ? "" : url.trim();
        username = username == null ? "" : username.trim();
        password = password == null ? "" : password;
        driverClassName = driverClassName == null || driverClassName.isBlank()
                ? "org.postgresql.Driver"
                : driverClassName.trim();
        schema = schema == null || schema.isBlank() ? "public" : schema.trim();
        poolMaxSize = poolMaxSize <= 0 ? 10 : poolMaxSize;
        poolMinIdle = poolMinIdle < 0 ? 2 : poolMinIdle;
        connectionTimeoutMs = connectionTimeoutMs <= 0 ? 30_000L : connectionTimeoutMs;
    }
}
