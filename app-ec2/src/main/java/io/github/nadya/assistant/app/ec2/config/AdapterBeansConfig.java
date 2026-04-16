package io.github.nadya.assistant.app.ec2.config;

import io.github.nadya.assistant.adapter.in.telegram.polling.client.TelegramBotApiPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.client.TelegramPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.client.StubTelegramPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.config.TelegramPollingProperties;
import io.github.nadya.assistant.adapter.in.telegram.polling.mapper.TelegramUpdateMapper;
import io.github.nadya.assistant.adapter.in.telegram.polling.service.TelegramPollingLoop;
import io.github.nadya.assistant.adapter.out.gemini.config.GeminiProperties;
import io.github.nadya.assistant.adapter.out.gemini.mapper.GeminiInterpretationMapper;
import io.github.nadya.assistant.adapter.out.gemini.service.GeminiIntentInterpreterAdapter;
import io.github.nadya.assistant.adapter.out.google.calendar.client.GoogleCalendarClient;
import io.github.nadya.assistant.adapter.out.google.calendar.client.StubGoogleCalendarClient;
import io.github.nadya.assistant.adapter.out.google.calendar.config.GoogleCalendarProperties;
import io.github.nadya.assistant.adapter.out.google.calendar.mapper.GoogleCalendarRequestMapper;
import io.github.nadya.assistant.adapter.out.google.calendar.oauth.GoogleCalendarOAuthSupport;
import io.github.nadya.assistant.adapter.out.google.calendar.service.GoogleCalendarAdapter;
import io.github.nadya.assistant.adapter.out.notification.telegram.client.StubTelegramNotificationClient;
import io.github.nadya.assistant.adapter.out.notification.telegram.client.TelegramNotificationClient;
import io.github.nadya.assistant.adapter.out.notification.telegram.config.TelegramNotificationProperties;
import io.github.nadya.assistant.adapter.out.notification.telegram.mapper.TelegramNotificationMapper;
import io.github.nadya.assistant.adapter.out.notification.telegram.service.TelegramNotificationAdapter;
import io.github.nadya.assistant.adapter.out.persistence.memory.InMemoryAuditAdapter;
import io.github.nadya.assistant.adapter.out.persistence.memory.InMemoryConversationStateAdapter;
import io.github.nadya.assistant.adapter.out.persistence.memory.InMemoryIdempotencyAdapter;
import io.github.nadya.assistant.adapter.out.persistence.memory.InMemoryUserContextAdapter;
import io.github.nadya.assistant.ports.in.HandleIncomingMessageUseCase;
import io.github.nadya.assistant.ports.out.AuditPort;
import io.github.nadya.assistant.ports.out.CalendarPort;
import io.github.nadya.assistant.ports.out.ConversationStatePort;
import io.github.nadya.assistant.ports.out.IdempotencyPort;
import io.github.nadya.assistant.ports.out.IntentInterpreterPort;
import io.github.nadya.assistant.ports.out.NotificationPort;
import io.github.nadya.assistant.ports.out.UserContextPort;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;

@Configuration
public class AdapterBeansConfig {

    @Bean
    UserContextPort userContextPort() {
        return new InMemoryUserContextAdapter();
    }

    @Bean
    ConversationStatePort conversationStatePort() {
        return new InMemoryConversationStateAdapter();
    }

    @Bean
    IdempotencyPort idempotencyPort() {
        return new InMemoryIdempotencyAdapter();
    }

    @Bean
    AuditPort auditPort() {
        return new InMemoryAuditAdapter();
    }

    @Bean
    GeminiProperties geminiProperties(Environment environment) {
        return bindOrDefault(
                environment,
                "assistant.gemini",
                GeminiProperties.class,
                new GeminiProperties("gemini-2.5-flash", "", true)
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
    GoogleCalendarProperties googleCalendarProperties(Environment environment) {
        return bindOrDefault(
                environment,
                "assistant.google-calendar",
                GoogleCalendarProperties.class,
                new GoogleCalendarProperties(false, "primary", true)
        );
    }

    @Bean
    GoogleCalendarClient googleCalendarClient() {
        return new StubGoogleCalendarClient();
    }

    @Bean
    GoogleCalendarRequestMapper googleCalendarRequestMapper() {
        return new GoogleCalendarRequestMapper();
    }

    @Bean
    GoogleCalendarOAuthSupport googleCalendarOAuthSupport() {
        return new GoogleCalendarOAuthSupport();
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
    TelegramNotificationProperties telegramNotificationProperties(Environment environment) {
        return bindOrDefault(
                environment,
                "assistant.telegram.notification",
                TelegramNotificationProperties.class,
                new TelegramNotificationProperties(false, "", true)
        );
    }

    @Bean
    TelegramNotificationClient telegramNotificationClient() {
        return new StubTelegramNotificationClient();
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
    TelegramPollingProperties telegramPollingProperties(Environment environment) {
        return bindOrDefault(
                environment,
                "assistant.telegram.polling",
                TelegramPollingProperties.class,
                new TelegramPollingProperties(false, "", Duration.ofSeconds(5), 100, true, java.util.List.of(), 10001L, 20001L)
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

    private <T> T bindOrDefault(Environment environment, String prefix, Class<T> targetType, T defaultValue) {
        return Binder.get(environment)
                .bind(prefix, Bindable.of(targetType))
                .orElse(defaultValue);
    }
}
