package io.github.nadya.assistant.app.ec2.config;

import io.github.nadya.assistant.adapter.in.telegram.polling.client.StubTelegramPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.client.TelegramBotApiPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.client.TelegramPollingClient;
import io.github.nadya.assistant.adapter.out.google.calendar.service.GoogleCalendarAdapter;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.JdbcAuditAdapter;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.JdbcConversationStateAdapter;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.JdbcUserContextAdapter;
import io.github.nadya.assistant.adapter.out.persistence.memory.InMemoryAuditAdapter;
import io.github.nadya.assistant.adapter.out.persistence.memory.InMemoryConversationStateAdapter;
import io.github.nadya.assistant.adapter.out.persistence.memory.InMemoryUserContextAdapter;
import io.github.nadya.assistant.app.ec2.AssistantEc2Application;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantGeminiProperties;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantPersistenceJdbcProperties;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantPersistenceProperties;
import io.github.nadya.assistant.ports.out.AuditPort;
import io.github.nadya.assistant.ports.out.CalendarPort;
import io.github.nadya.assistant.ports.out.ConversationStatePort;
import io.github.nadya.assistant.ports.out.UserContextPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileConfigurationIntegrationTest {

    @Test
    void shouldUseStubAndInMemoryAdaptersInLocalProfile() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AssistantEc2Application.class)
                .profiles("local")
                .properties(Map.ofEntries(
                        Map.entry("TELEGRAM_POLLING_ENABLED", false),
                        Map.entry("TELEGRAM_NOTIFICATION_ENABLED", false)
                ))
                .run()) {
            assertInstanceOf(InMemoryUserContextAdapter.class, context.getBean("userContextPort", UserContextPort.class));
            assertInstanceOf(InMemoryConversationStateAdapter.class, context.getBean("conversationStatePort", ConversationStatePort.class));
            assertInstanceOf(InMemoryAuditAdapter.class, context.getBean("auditPort", AuditPort.class));
            assertInstanceOf(StubTelegramPollingClient.class, context.getBean(TelegramPollingClient.class));
            assertTrue(context.getBean(AssistantGeminiProperties.class).stubMode());
        }
    }

    @Test
    void shouldUseJdbcAndRealModeAdaptersInSmokeProfile() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AssistantEc2Application.class)
                .profiles("smoke")
                .properties(Map.ofEntries(
                        Map.entry("TELEGRAM_POLLING_ENABLED", false),
                        Map.entry("DB_URL", "jdbc:h2:mem:smoke-profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"),
                        Map.entry("DB_USERNAME", "sa"),
                        Map.entry("DB_PASSWORD", ""),
                        Map.entry("DB_DRIVER", "org.h2.Driver"),
                        Map.entry("TELEGRAM_BOT_TOKEN", "test-bot-token"),
                        Map.entry("GEMINI_API_KEY", "test-gemini-key"),
                        Map.entry("GOOGLE_OAUTH_CLIENT_ID", "test-client-id"),
                        Map.entry("GOOGLE_OAUTH_CLIENT_SECRET", "test-client-secret"),
                        Map.entry("GOOGLE_OAUTH_REFRESH_TOKEN", "test-refresh-token")
                ))
                .run()) {
            AssistantPersistenceProperties persistenceProperties = context.getBean(AssistantPersistenceProperties.class);
            AssistantPersistenceJdbcProperties jdbcProperties = context.getBean(AssistantPersistenceJdbcProperties.class);

            assertTrue(persistenceProperties.usesJdbcState());
            assertTrue(persistenceProperties.usesJdbcAudit());
            assertFalse(jdbcProperties.url().isBlank());
            assertInstanceOf(JdbcUserContextAdapter.class, context.getBean("userContextPort", UserContextPort.class));
            assertInstanceOf(JdbcConversationStateAdapter.class, context.getBean("conversationStatePort", ConversationStatePort.class));
            assertInstanceOf(JdbcAuditAdapter.class, context.getBean("auditPort", AuditPort.class));
            assertInstanceOf(TelegramBotApiPollingClient.class, context.getBean(TelegramPollingClient.class));
            assertInstanceOf(GoogleCalendarAdapter.class, context.getBean(CalendarPort.class));
            assertFalse(context.getBean(AssistantGeminiProperties.class).stubMode());
        }
    }
}
