package io.github.nadya.assistant.app.ec2.config.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SystemProxyEnvironmentPostProcessorTest {

    private static final String SYSTEM_PROXY_PROPERTY = "java.net.useSystemProxies";

    private final SystemProxyEnvironmentPostProcessor postProcessor = new SystemProxyEnvironmentPostProcessor();

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty(SYSTEM_PROXY_PROPERTY);
    }

    @Test
    void shouldEnableJvmSystemProxyWhenAssistantPropertyIsTrue() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("assistant.http.use-system-proxy", "true");

        postProcessor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertEquals("true", System.getProperty(SYSTEM_PROXY_PROPERTY));
    }

    @Test
    void shouldLeaveJvmSystemProxyUntouchedWhenAssistantPropertyIsFalse() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("assistant.http.use-system-proxy", "false");

        postProcessor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertNull(System.getProperty(SYSTEM_PROXY_PROPERTY));
    }
}
