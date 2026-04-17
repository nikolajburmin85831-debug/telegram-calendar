package io.github.nadya.assistant.app.ec2.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public final class JdbcPersistenceEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String mode = context.getEnvironment().getProperty("assistant.persistence.mode", "inmemory");
        String auditMode = context.getEnvironment().getProperty("assistant.persistence.audit-mode", "inmemory");
        return "jdbc".equalsIgnoreCase(mode) || "jdbc".equalsIgnoreCase(auditMode);
    }
}
