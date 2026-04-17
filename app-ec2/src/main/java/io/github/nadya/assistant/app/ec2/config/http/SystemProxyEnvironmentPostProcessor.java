package io.github.nadya.assistant.app.ec2.config.http;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

public final class SystemProxyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String SYSTEM_PROXY_PROPERTY = "java.net.useSystemProxies";
    private static final String ASSISTANT_SYSTEM_PROXY_PROPERTY = "assistant.http.use-system-proxy";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application
    ) {
        if (environment.getProperty(ASSISTANT_SYSTEM_PROXY_PROPERTY, Boolean.class, false)) {
            System.setProperty(SYSTEM_PROXY_PROPERTY, "true");
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
