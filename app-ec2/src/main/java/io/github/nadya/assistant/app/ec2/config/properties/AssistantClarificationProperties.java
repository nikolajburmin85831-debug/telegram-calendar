package io.github.nadya.assistant.app.ec2.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant.clarification")
public record AssistantClarificationProperties(
        int maxInvalidAttempts
) {

    public AssistantClarificationProperties {
        maxInvalidAttempts = maxInvalidAttempts <= 0 ? 2 : maxInvalidAttempts;
    }
}
