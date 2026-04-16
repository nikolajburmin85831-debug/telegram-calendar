package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.application.command.MessageHandlingContext;
import io.github.nadya.assistant.application.handler.ClarificationHandler;
import io.github.nadya.assistant.application.handler.CreateCalendarEventHandler;
import io.github.nadya.assistant.application.handler.PendingConfirmationHandler;
import io.github.nadya.assistant.application.orchestration.IntentRoutingService;
import io.github.nadya.assistant.application.result.HandlingOutcome;
import io.github.nadya.assistant.domain.conversation.ConversationState;
import io.github.nadya.assistant.domain.conversation.ConversationStatus;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.execution.ExecutionResult;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.policy.ExecutionDecision;
import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.ports.in.HandleIncomingMessageUseCase;
import io.github.nadya.assistant.ports.out.AuditEntry;
import io.github.nadya.assistant.ports.out.AuditPort;
import io.github.nadya.assistant.ports.out.ConversationStatePort;
import io.github.nadya.assistant.ports.out.IdempotencyPort;
import io.github.nadya.assistant.ports.out.IntentInterpretationRequest;
import io.github.nadya.assistant.ports.out.IntentInterpreterPort;
import io.github.nadya.assistant.ports.out.NotificationCommand;
import io.github.nadya.assistant.ports.out.NotificationPort;
import io.github.nadya.assistant.ports.out.UserContextPort;

import java.time.Instant;

public final class HandleIncomingMessageService implements HandleIncomingMessageUseCase {

    private final UserContextPort userContextPort;
    private final ConversationStatePort conversationStatePort;
    private final IdempotencyPort idempotencyPort;
    private final IntentInterpreterPort intentInterpreterPort;
    private final NotificationPort notificationPort;
    private final AuditPort auditPort;
    private final IntentRoutingService intentRoutingService;
    private final CreateCalendarEventHandler createCalendarEventHandler;
    private final ClarificationHandler clarificationHandler;
    private final PendingConfirmationHandler pendingConfirmationHandler;

    public HandleIncomingMessageService(
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
        this.userContextPort = userContextPort;
        this.conversationStatePort = conversationStatePort;
        this.idempotencyPort = idempotencyPort;
        this.intentInterpreterPort = intentInterpreterPort;
        this.notificationPort = notificationPort;
        this.auditPort = auditPort;
        this.intentRoutingService = intentRoutingService;
        this.createCalendarEventHandler = createCalendarEventHandler;
        this.clarificationHandler = clarificationHandler;
        this.pendingConfirmationHandler = pendingConfirmationHandler;
    }

    @Override
    public ExecutionResult handle(IncomingUserMessage message) {
        String idempotencyKey = buildIdempotencyKey(message);
        if (!idempotencyPort.registerIfAbsent(idempotencyKey)) {
            auditPort.record(new AuditEntry(
                    message.userId(),
                    message.conversationId(),
                    "DUPLICATE_MESSAGE_SKIPPED",
                    Instant.now(),
                    idempotencyKey
            ));
            return ExecutionResult.skipped("duplicate_message_skipped");
        }

        UserContext userContext = loadUserContext(message);
        ConversationState conversationState = loadConversationState(message);

        try {
            // TODO: resume pending clarification/confirmation flows from persisted conversation state.
            IntentInterpretation interpretation = intentInterpreterPort.interpret(
                    new IntentInterpretationRequest(message, userContext, conversationState)
            );
            ExecutionDecision decision = intentRoutingService.decide(message, userContext, interpretation);
            HandlingOutcome outcome = handleDecision(message, userContext, conversationState, interpretation, decision);

            conversationStatePort.save(outcome.nextConversationState());
            notifyUser(message, outcome.executionResult());
            auditPort.record(new AuditEntry(
                    message.userId(),
                    message.conversationId(),
                    outcome.nextConversationState().status().name(),
                    Instant.now(),
                    outcome.executionResult().auditDetails()
            ));
            return outcome.executionResult();
        } catch (RuntimeException exception) {
            ConversationState failedState = conversationState.failed();
            conversationStatePort.save(failedState);

            ExecutionResult failure = ExecutionResult.failed(
                    "Не удалось обработать сообщение. Попробуйте повторить запрос позже.",
                    exception.getClass().getSimpleName()
            );
            notifyUser(message, failure);
            auditPort.record(new AuditEntry(
                    message.userId(),
                    message.conversationId(),
                    ConversationStatus.FAILED.name(),
                    Instant.now(),
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()
            ));
            return failure;
        }
    }

    private String buildIdempotencyKey(IncomingUserMessage message) {
        String stableExternalId = message.externalMessageId().isBlank()
                ? message.internalMessageId()
                : message.externalMessageId();
        return "%s:%s".formatted(message.channelType().name(), stableExternalId);
    }

    private UserContext loadUserContext(IncomingUserMessage message) {
        UserContext userContext = userContextPort.findByUserId(message.userId())
                .orElseGet(() -> UserContext.defaultFor(message.userId(), message.conversationId()))
                .withActiveConversationId(message.conversationId());
        return userContextPort.save(userContext);
    }

    private ConversationState loadConversationState(IncomingUserMessage message) {
        return conversationStatePort.findByConversationId(message.conversationId())
                .orElseGet(() -> ConversationState.idle(message.conversationId(), message.userId()));
    }

    private HandlingOutcome handleDecision(
            IncomingUserMessage message,
            UserContext userContext,
            ConversationState conversationState,
            IntentInterpretation interpretation,
            ExecutionDecision decision
    ) {
        return switch (decision.outcome()) {
            case EXECUTE_NOW -> {
                ConversationState executingState = conversationState.executing();
                conversationStatePort.save(executingState);
                yield createCalendarEventHandler.handle(
                        new MessageHandlingContext(message, userContext, executingState, interpretation)
                );
            }
            case ASK_FOR_CLARIFICATION -> clarificationHandler.handle(conversationState, decision.clarificationRequest());
            case ASK_FOR_CONFIRMATION -> pendingConfirmationHandler.handle(conversationState, decision.pendingConfirmation());
            case REJECT, DEFER -> new HandlingOutcome(
                    ExecutionResult.rejected(decision.rejectionReason(), "intent_rejected"),
                    conversationState.failed()
            );
        };
    }

    private void notifyUser(IncomingUserMessage message, ExecutionResult executionResult) {
        if (executionResult.userSummary() == null || executionResult.userSummary().isBlank()) {
            return;
        }

        notificationPort.send(new NotificationCommand(
                message.userId(),
                message.conversationId(),
                executionResult.userSummary()
        ));
    }
}
