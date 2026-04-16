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
import io.github.nadya.assistant.domain.conversation.PendingAction;
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
    private final PendingActionFactory pendingActionFactory;
    private final PendingActionMergeService pendingActionMergeService;
    private final ConversationControlService conversationControlService;

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
            PendingConfirmationHandler pendingConfirmationHandler,
            PendingActionFactory pendingActionFactory,
            PendingActionMergeService pendingActionMergeService,
            ConversationControlService conversationControlService
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
        this.pendingActionFactory = pendingActionFactory;
        this.pendingActionMergeService = pendingActionMergeService;
        this.conversationControlService = conversationControlService;
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
            HandlingOutcome outcome = resumePendingIfPossible(message, userContext, conversationState);
            if (outcome == null) {
                outcome = handleNewMessage(message, userContext, conversationState);
            }

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

    private HandlingOutcome resumePendingIfPossible(
            IncomingUserMessage message,
            UserContext userContext,
            ConversationState conversationState
    ) {
        if (conversationState.isAwaitingConfirmation()) {
            return handlePendingConfirmation(message, userContext, conversationState);
        }
        if (conversationState.isAwaitingClarification()) {
            return handlePendingClarification(message, userContext, conversationState);
        }
        return null;
    }

    private HandlingOutcome handleNewMessage(
            IncomingUserMessage message,
            UserContext userContext,
            ConversationState conversationState
    ) {
        IntentInterpretation interpretation = intentInterpreterPort.interpret(
                new IntentInterpretationRequest(message, userContext, conversationState)
        );
        ExecutionDecision decision = intentRoutingService.decide(message, userContext, interpretation);
        return handleDecision(message, message, userContext, conversationState, interpretation, decision);
    }

    private HandlingOutcome handlePendingClarification(
            IncomingUserMessage message,
            UserContext userContext,
            ConversationState conversationState
    ) {
        if (conversationControlService.isCancelCommand(message.text())) {
            return cancellationOutcome(
                    conversationState,
                    "Хорошо, отменяю текущее действие.",
                    "pending_clarification_cancelled"
            );
        }

        PendingAction pendingAction = conversationState.pendingAction();
        IntentInterpretation followUpInterpretation = intentInterpreterPort.interpret(
                new IntentInterpretationRequest(message, userContext, conversationState)
        );
        IntentInterpretation mergedInterpretation = pendingActionMergeService.merge(
                pendingAction,
                conversationState.clarificationRequest(),
                followUpInterpretation
        );
        ExecutionDecision decision = intentRoutingService.decide(message, userContext, mergedInterpretation);
        return handleDecision(
                message,
                pendingAction.sourceMessage(),
                userContext,
                conversationState,
                mergedInterpretation,
                decision
        );
    }

    private HandlingOutcome handlePendingConfirmation(
            IncomingUserMessage message,
            UserContext userContext,
            ConversationState conversationState
    ) {
        return switch (conversationControlService.resolveConfirmationReply(message.text())) {
            case APPROVE -> handleDecision(
                    message,
                    conversationState.pendingAction().sourceMessage(),
                    userContext,
                    conversationState,
                    conversationState.pendingAction().interpretation(),
                    ExecutionDecision.executeNow()
            );
            case REJECT -> cancellationOutcome(
                    conversationState,
                    "Хорошо, не выполняю это действие.",
                    "pending_confirmation_rejected"
            );
            case CANCEL -> cancellationOutcome(
                    conversationState,
                    "Хорошо, отменяю текущее действие.",
                    "pending_confirmation_cancelled"
            );
            case INVALID -> pendingConfirmationHandler.handleInvalidResponse(
                    conversationState,
                    conversationState.pendingConfirmation()
            );
        };
    }

    private HandlingOutcome handleDecision(
            IncomingUserMessage triggeringMessage,
            IncomingUserMessage sourceMessage,
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
                        new MessageHandlingContext(
                                triggeringMessage,
                                sourceMessage,
                                userContext,
                                executingState,
                                interpretation
                        )
                );
            }
            case ASK_FOR_CLARIFICATION -> clarificationHandler.handle(
                    conversationState,
                    decision.clarificationRequest(),
                    pendingActionFactory.create(sourceMessage, interpretation)
            );
            case ASK_FOR_CONFIRMATION -> pendingConfirmationHandler.handle(
                    conversationState,
                    decision.pendingConfirmation(),
                    pendingActionFactory.create(sourceMessage, interpretation)
            );
            case REJECT, DEFER -> new HandlingOutcome(
                    ExecutionResult.rejected(decision.rejectionReason(), "intent_rejected"),
                    conversationState.failed()
            );
        };
    }

    private HandlingOutcome cancellationOutcome(
            ConversationState conversationState,
            String userSummary,
            String auditDetails
    ) {
        return new HandlingOutcome(
                ExecutionResult.cancelled(userSummary, auditDetails),
                conversationState.cancelled()
        );
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
