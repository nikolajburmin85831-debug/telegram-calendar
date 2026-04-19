package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.domain.intent.IntentInterpretation;
import io.github.nadya.assistant.domain.intent.IntentType;
import io.github.nadya.assistant.domain.user.ConfirmationPreference;
import io.github.nadya.assistant.domain.user.UserContext;

public final class ConfirmationPolicyService {

    public boolean requiresConfirmation(UserContext userContext, IntentInterpretation interpretation) {
        if (interpretation.intentType() != IntentType.CREATE_CALENDAR_EVENT) {
            return false;
        }
        if (userContext.confirmationPreference() == ConfirmationPreference.REQUIRE_CONFIRMATION) {
            return true;
        }
        return interpretation.confidenceScore().value() < 0.60d;
    }
}
