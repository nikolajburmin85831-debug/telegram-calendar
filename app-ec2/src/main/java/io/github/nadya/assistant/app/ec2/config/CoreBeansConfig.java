package io.github.nadya.assistant.app.ec2.config;

import io.github.nadya.assistant.application.handler.ClarificationHandler;
import io.github.nadya.assistant.application.handler.CreateCalendarEventHandler;
import io.github.nadya.assistant.application.handler.PendingConfirmationHandler;
import io.github.nadya.assistant.application.orchestration.IntentRoutingService;
import io.github.nadya.assistant.application.service.ConfirmationPolicyService;
import io.github.nadya.assistant.application.service.HandleIncomingMessageService;
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

@Configuration
public class CoreBeansConfig {

    @Bean
    ConfirmationPolicyService confirmationPolicyService() {
        return new ConfirmationPolicyService();
    }

    @Bean
    IntentRoutingService intentRoutingService(ConfirmationPolicyService confirmationPolicyService) {
        return new IntentRoutingService(confirmationPolicyService);
    }

    @Bean
    CreateCalendarEventHandler createCalendarEventHandler(CalendarPort calendarPort) {
        return new CreateCalendarEventHandler(calendarPort);
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
    HandleIncomingMessageUseCase handleIncomingMessageUseCase(
            UserContextPort userContextPort,
            ConversationStatePort conversationStatePort,
            IdempotencyPort idempotencyPort,
            IntentInterpreterPort intentInterpreterPort,
            NotificationPort notificationPort,
            AuditPort auditPort,
            IntentRoutingService intentRoutingService,
            CreateCalendarEventHandler createCalendarEventHandler,
            ClarificationHandler clarificationHandler,
            PendingConfirmationHandler pendingConfirmationHandler
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
                clarificationHandler,
                pendingConfirmationHandler
        );
    }
}
