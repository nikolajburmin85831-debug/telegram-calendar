package io.github.nadya.assistant.app.ec2.config;

import io.github.nadya.assistant.adapter.in.telegram.polling.client.StubTelegramPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.client.TelegramBotApiPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.client.TelegramPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.config.TelegramPollingProperties;
import io.github.nadya.assistant.adapter.in.telegram.polling.mapper.TelegramUpdateMapper;
import io.github.nadya.assistant.adapter.in.telegram.polling.service.TelegramPollingLoop;
import io.github.nadya.assistant.adapter.out.gemini.config.GeminiProperties;
import io.github.nadya.assistant.adapter.out.gemini.mapper.GeminiInterpretationMapper;
import io.github.nadya.assistant.adapter.out.gemini.service.GeminiIntentInterpreterAdapter;
import io.github.nadya.assistant.adapter.out.google.calendar.client.GoogleCalendarClient;
import io.github.nadya.assistant.adapter.out.google.calendar.client.HttpGoogleCalendarClient;
import io.github.nadya.assistant.adapter.out.google.calendar.client.StubGoogleCalendarClient;
import io.github.nadya.assistant.adapter.out.google.calendar.config.GoogleCalendarProperties;
import io.github.nadya.assistant.adapter.out.google.calendar.mapper.GoogleCalendarRequestMapper;
import io.github.nadya.assistant.adapter.out.google.calendar.oauth.GoogleCalendarOAuthSupport;
import io.github.nadya.assistant.adapter.out.google.calendar.oauth.GoogleOAuthProperties;
import io.github.nadya.assistant.adapter.out.google.calendar.service.GoogleCalendarAdapter;
import io.github.nadya.assistant.adapter.out.notification.telegram.client.StubTelegramNotificationClient;
import io.github.nadya.assistant.adapter.out.notification.telegram.client.TelegramBotApiNotificationClient;
import io.github.nadya.assistant.adapter.out.notification.telegram.client.TelegramNotificationClient;
import io.github.nadya.assistant.adapter.out.notification.telegram.config.TelegramNotificationProperties;
import io.github.nadya.assistant.adapter.out.notification.telegram.mapper.TelegramNotificationMapper;
import io.github.nadya.assistant.adapter.out.notification.telegram.service.TelegramNotificationAdapter;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.JdbcAuditAdapter;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.JdbcConversationStateAdapter;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.JdbcIdempotencyAdapter;
import io.github.nadya.assistant.adapter.out.persistence.jdbc.JdbcUserContextAdapter;
import io.github.nadya.assistant.adapter.out.persistence.memory.InMemoryAuditAdapter;
import io.github.nadya.assistant.adapter.out.persistence.memory.InMemoryConversationStateAdapter;
import io.github.nadya.assistant.adapter.out.persistence.memory.InMemoryIdempotencyAdapter;
import io.github.nadya.assistant.adapter.out.persistence.memory.InMemoryUserContextAdapter;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantGeminiProperties;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantGoogleCalendarProperties;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantGoogleOAuthProperties;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantPersistenceProperties;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantTelegramProperties;
import io.github.nadya.assistant.ports.in.HandleIncomingMessageUseCase;
import io.github.nadya.assistant.ports.out.AuditPort;
import io.github.nadya.assistant.ports.out.CalendarPort;
import io.github.nadya.assistant.ports.out.ConversationStatePort;
import io.github.nadya.assistant.ports.out.IdempotencyPort;
import io.github.nadya.assistant.ports.out.IntentInterpreterPort;
import io.github.nadya.assistant.ports.out.NotificationPort;
import io.github.nadya.assistant.ports.out.UserContextPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AdapterBeansConfig {

    @Bean
    UserContextPort userContextPort(
            AssistantPersistenceProperties persistenceProperties,
            ObjectProvider<JdbcUserContextAdapter> jdbcUserContextAdapter
    ) {
        if (persistenceProperties.usesJdbcState()) {
            return jdbcUserContextAdapter.getObject();
        }
        return new InMemoryUserContextAdapter();
    }

    @Bean
    ConversationStatePort conversationStatePort(
            AssistantPersistenceProperties persistenceProperties,
            ObjectProvider<JdbcConversationStateAdapter> jdbcConversationStateAdapter
    ) {
        if (persistenceProperties.usesJdbcState()) {
            return jdbcConversationStateAdapter.getObject();
        }
        return new InMemoryConversationStateAdapter();
    }

    @Bean
    IdempotencyPort idempotencyPort(
            AssistantPersistenceProperties persistenceProperties,
            ObjectProvider<JdbcIdempotencyAdapter> jdbcIdempotencyAdapter
    ) {
        if (persistenceProperties.usesJdbcState()) {
            return jdbcIdempotencyAdapter.getObject();
        }
        return new InMemoryIdempotencyAdapter();
    }

    @Bean
    AuditPort auditPort(
            AssistantPersistenceProperties persistenceProperties,
            ObjectProvider<JdbcAuditAdapter> jdbcAuditAdapter
    ) {
        if (persistenceProperties.usesJdbcAudit()) {
            return jdbcAuditAdapter.getObject();
        }
        return new InMemoryAuditAdapter();
    }

    @Bean
    GeminiProperties geminiProperties(AssistantGeminiProperties properties) {
        return new GeminiProperties(
                properties.model(),
                properties.apiKey(),
                properties.stubMode(),
                properties.baseUrl(),
                properties.fallbackToStubOnError()
        );
    }

    @Bean
    GeminiInterpretationMapper geminiInterpretationMapper() {
        return new GeminiInterpretationMapper();
    }

    @Bean
    IntentInterpreterPort intentInterpreterPort(
            GeminiProperties geminiProperties,
            GeminiInterpretationMapper geminiInterpretationMapper
    ) {
        return new GeminiIntentInterpreterAdapter(geminiProperties, geminiInterpretationMapper);
    }

    @Bean
    GoogleCalendarProperties googleCalendarProperties(AssistantGoogleCalendarProperties properties) {
        return new GoogleCalendarProperties(
                properties.runtimeEnabled(),
                properties.calendarId(),
                properties.stubMode(),
                properties.baseUrl()
        );
    }

    @Bean
    GoogleOAuthProperties googleOAuthProperties(AssistantGoogleOAuthProperties properties) {
        return new GoogleOAuthProperties(
                properties.clientId(),
                properties.clientSecret(),
                properties.refreshToken(),
                properties.accessToken(),
                properties.tokenUrl(),
                properties.parsedScopes(),
                properties.credentialSource()
        );
    }

    @Bean
    GoogleCalendarOAuthSupport googleCalendarOAuthSupport(GoogleOAuthProperties googleOAuthProperties) {
        return new GoogleCalendarOAuthSupport(googleOAuthProperties);
    }

    @Bean
    GoogleCalendarClient googleCalendarClient(
            GoogleCalendarProperties googleCalendarProperties,
            GoogleCalendarOAuthSupport googleCalendarOAuthSupport
    ) {
        if (googleCalendarProperties.stubMode()) {
            return new StubGoogleCalendarClient();
        }
        return new HttpGoogleCalendarClient(googleCalendarProperties, googleCalendarOAuthSupport);
    }

    @Bean
    GoogleCalendarRequestMapper googleCalendarRequestMapper() {
        return new GoogleCalendarRequestMapper();
    }

    @Bean
    CalendarPort calendarPort(
            GoogleCalendarClient googleCalendarClient,
            GoogleCalendarRequestMapper googleCalendarRequestMapper,
            GoogleCalendarOAuthSupport googleCalendarOAuthSupport,
            GoogleCalendarProperties googleCalendarProperties
    ) {
        return new GoogleCalendarAdapter(
                googleCalendarClient,
                googleCalendarRequestMapper,
                googleCalendarOAuthSupport,
                googleCalendarProperties
        );
    }

    @Bean
    TelegramNotificationProperties telegramNotificationProperties(AssistantTelegramProperties properties) {
        return new TelegramNotificationProperties(
                properties.notification().enabled(),
                properties.botToken(),
                properties.stubMode(),
                properties.apiBaseUrl()
        );
    }

    @Bean
    TelegramNotificationClient telegramNotificationClient(TelegramNotificationProperties properties) {
        if (properties.stubMode()) {
            return new StubTelegramNotificationClient();
        }
        return new TelegramBotApiNotificationClient(properties.apiBaseUrl());
    }

    @Bean
    TelegramNotificationMapper telegramNotificationMapper() {
        return new TelegramNotificationMapper();
    }

    @Bean
    NotificationPort notificationPort(
            TelegramNotificationClient telegramNotificationClient,
            TelegramNotificationMapper telegramNotificationMapper,
            TelegramNotificationProperties telegramNotificationProperties
    ) {
        return new TelegramNotificationAdapter(
                telegramNotificationClient,
                telegramNotificationMapper,
                telegramNotificationProperties
        );
    }

    @Bean
    TelegramPollingProperties telegramPollingProperties(AssistantTelegramProperties properties) {
        return new TelegramPollingProperties(
                properties.polling().enabled(),
                properties.botToken(),
                Duration.ofMillis(properties.polling().fixedDelayMs()),
                properties.polling().timeoutSeconds(),
                properties.polling().limit(),
                properties.stubMode(),
                properties.polling().sampleMessages(),
                properties.polling().stubUserId(),
                properties.polling().stubChatId(),
                properties.apiBaseUrl()
        );
    }

    @Bean
    TelegramPollingClient telegramPollingClient(TelegramPollingProperties telegramPollingProperties) {
        if (telegramPollingProperties.stubMode()) {
            return new StubTelegramPollingClient(telegramPollingProperties);
        }
        return new TelegramBotApiPollingClient(telegramPollingProperties);
    }

    @Bean
    TelegramUpdateMapper telegramUpdateMapper() {
        return new TelegramUpdateMapper();
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    TelegramPollingLoop telegramPollingLoop(
            TelegramPollingClient telegramPollingClient,
            TelegramUpdateMapper telegramUpdateMapper,
            HandleIncomingMessageUseCase handleIncomingMessageUseCase,
            TelegramPollingProperties telegramPollingProperties
    ) {
        return new TelegramPollingLoop(
                telegramPollingClient,
                telegramUpdateMapper,
                handleIncomingMessageUseCase,
                telegramPollingProperties
        );
    }
}
