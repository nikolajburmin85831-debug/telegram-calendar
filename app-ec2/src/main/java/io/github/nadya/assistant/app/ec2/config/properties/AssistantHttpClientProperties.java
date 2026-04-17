package io.github.nadya.assistant.app.ec2.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant.http")
public record AssistantHttpClientProperties(
        long connectTimeoutSeconds,
        boolean useSystemProxy,
        Proxy proxy
) {

    public AssistantHttpClientProperties {
        connectTimeoutSeconds = connectTimeoutSeconds <= 0 ? 15L : connectTimeoutSeconds;
        proxy = proxy == null ? new Proxy(false, "", "", "") : proxy;
    }

    public record Proxy(
            boolean enabled,
            String url,
            String username,
            String password
    ) {
        public Proxy {
            url = url == null ? "" : url.trim();
            username = username == null ? "" : username.trim();
            password = password == null ? "" : password;
        }
    }
}
