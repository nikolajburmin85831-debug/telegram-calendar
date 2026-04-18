package io.github.nadya.assistant.app.ec2.config;

import io.github.nadya.assistant.application.handler.ClarificationHandler;
import io.github.nadya.assistant.application.handler.CreateCalendarEventHandler;
import io.github.nadya.assistant.application.handler.PendingConfirmationHandler;
import io.github.nadya.assistant.application.orchestration.IntentRoutingService;
import io.github.nadya.assistant.application.service.CalendarEventDraftFactory;
import io.github.nadya.assistant.application.service.CalendarExecutionGuard;
import io.github.nadya.assistant.application.service.CalendarExecutionGuardSettings;
import io.github.nadya.assistant.application.service.ClarificationRetryService;
import io.github.nadya.assistant.application.service.ClarificationRetrySettings;
import io.github.nadya.assistant.application.service.ConfirmationPolicyService;
import io.github.nadya.assistant.application.service.ConversationControlService;
import io.github.nadya.assistant.application.service.DefaultUserContextFactory;
import io.github.nadya.assistant.application.service.HandleIncomingMessageService;
import io.github.nadya.assistant.application.service.HouseholdNotificationService;
import io.github.nadya.assistant.application.service.PendingActionFactory;
import io.github.nadya.assistant.application.service.PendingActionMergeService;
import io.github.nadya.assistant.application.service.PendingFlowInterruptionService;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantAppProperties;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantClarificationProperties;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantExecutionGuardProperties;
import io.github.nadya.assistant.app.ec2.config.properties.AssistantHouseholdProperties;
import io.github.nadya.assistant.domain.common.Timezone;
import io.github.nadya.assistant.domain.household.HouseholdNotificationSettings;
import io.github.nadya.assistant.ports.in.HandleIncomingMessageUseCase;
import io.github.nadya.assistant.ports.out.AuditPort;
import io.github.nadya.assistant.ports.out.CalendarPort;
import io.github.nadya.assistant.ports.out.ConversationStatePort;
import io.github.nadya.assistant.ports.out.IdempotencyPort;
import io.github.nadya.assistant.ports.out.IntentInterpreterPort;
import io.github.nadya.assistant.ports.out.NotificationPort;
import io.github.nadya.assistant.ports.out.UserContextPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CoreBeansConfig {

    @Bean
    Clock assistantClock() {
        return Clock.systemUTC();
    }

    @Bean
    ConfirmationPolicyService confirmationPolicyService() {
        return new ConfirmationPolicyService();
    }

    @Bean
    IntentRoutingService intentRoutingService(ConfirmationPolicyService confirmationPolicyService) {
        return new IntentRoutingService(confirmationPolicyService);
    }

    @Bean
    CalendarExecutionGuardSettings calendarExecutionGuardSettings(AssistantExecutionGuardProperties properties) {
        return new CalendarExecutionGuardSettings(
                properties.maxTitleLength(),
                properties.maxDescriptionLength(),
                properties.maxEventDuration(),
                properties.confirmationHorizonDays()
        );
    }

    @Bean
    CalendarExecutionGuard calendarExecutionGuard(
            CalendarExecutionGuardSettings settings,
            Clock assistantClock
    ) {
        return new CalendarExecutionGuard(settings, assistantClock);
    }

    @Bean
    CalendarEventDraftFactory calendarEventDraftFactory() {
        return new CalendarEventDraftFactory();
    }

    @Bean
    ClarificationRetrySettings clarificationRetrySettings(AssistantClarificationProperties properties) {
        return new ClarificationRetrySettings(properties.maxInvalidAttempts());
    }

    @Bean
    ClarificationRetryService clarificationRetryService(ClarificationRetrySettings settings) {
        return new ClarificationRetryService(settings);
    }

    @Bean
    HouseholdNotificationSettings householdNotificationSettings(AssistantHouseholdProperties properties) {
        return properties.toSettings();
    }

    @Bean
    HouseholdNotificationService householdNotificationService(HouseholdNotificationSettings settings) {
        return new HouseholdNotificationService(settings);
    }

    @Bean
    CreateCalendarEventHandler createCalendarEventHandler(
            CalendarPort calendarPort,
            HouseholdNotificationService householdNotificationService
    ) {
        return new CreateCalendarEventHandler(calendarPort, householdNotificationService);
    }

    @Bean
    ClarificationHandler clarificationHandler() {
        return new ClarificationHandler();
    }

    @Bean
    PendingConfirmationHandler pendingConfirmationHandler() {
        return new PendingConfirmationHandler();
    }

    @Bean
    PendingActionFactory pendingActionFactory() {
        return new PendingActionFactory();
    }

    @Bean
    PendingActionMergeService pendingActionMergeService() {
        return new PendingActionMergeService();
    }

    @Bean
    ConversationControlService conversationControlService() {
        return new ConversationControlService();
    }

    @Bean
    PendingFlowInterruptionService pendingFlowInterruptionService() {
        return new PendingFlowInterruptionService();
    }

    @Bean
    DefaultUserContextFactory defaultUserContextFactory(AssistantAppProperties appProperties) {
        return new DefaultUserContextFactory(
                new Timezone(appProperties.defaultTimezone()),
                appProperties.defaultLanguage(),
                appProperties.defaultConfirmationPreference(),
                appProperties.defaultEventDuration()
        );
    }

    @Bean
    HandleIncomingMessageUseCase handleIncomingMessageUseCase(
            UserContextPort userContextPort,
            ConversationStatePort conversationStatePort,
            IdempotencyPort idempotencyPort,
            IntentInterpreterPort intentInterpreterPort,
            NotificationPort notificationPort,
            AuditPort auditPort,
            IntentRoutingService intentRoutingService,
            CreateCalendarEventHandler createCalendarEventHandler,
            CalendarEventDraftFactory calendarEventDraftFactory,
            CalendarExecutionGuard calendarExecutionGuard,
            ClarificationRetryService clarificationRetryService,
            ClarificationHandler clarificationHandler,
            PendingConfirmationHandler pendingConfirmationHandler,
            PendingActionFactory pendingActionFactory,
            PendingActionMergeService pendingActionMergeService,
            ConversationControlService conversationControlService,
            PendingFlowInterruptionService pendingFlowInterruptionService,
            DefaultUserContextFactory defaultUserContextFactory
    ) {
        return new HandleIncomingMessageService(
                userContextPort,
                conversationStatePort,
                idempotencyPort,
                intentInterpreterPort,
                notificationPort,
                auditPort,
                intentRoutingService,
                createCalendarEventHandler,
                calendarEventDraftFactory,
                calendarExecutionGuard,
                clarificationRetryService,
                clarificationHandler,
                pendingConfirmationHandler,
                pendingActionFactory,
                pendingActionMergeService,
                conversationControlService,
                pendingFlowInterruptionService,
                defaultUserContextFactory
        );
    }
}
