package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.domain.common.Timezone;
import io.github.nadya.assistant.domain.user.ConfirmationPreference;
import io.github.nadya.assistant.domain.user.UserContext;
import io.github.nadya.assistant.domain.user.UserIdentity;

import java.time.Duration;

public final class DefaultUserContextFactory {

    private final Timezone defaultTimezone;
    private final String defaultLanguage;
    private final ConfirmationPreference defaultConfirmationPreference;
    private final Duration defaultEventDuration;

    public DefaultUserContextFactory(
            Timezone defaultTimezone,
            String defaultLanguage,
            ConfirmationPreference defaultConfirmationPreference,
            Duration defaultEventDuration
    ) {
        this.defaultTimezone = defaultTimezone;
        this.defaultLanguage = defaultLanguage == null || defaultLanguage.isBlank() ? "ru" : defaultLanguage;
        this.defaultConfirmationPreference = defaultConfirmationPreference == null
                ? ConfirmationPreference.AUTO_EXECUTE
                : defaultConfirmationPreference;
        this.defaultEventDuration = defaultEventDuration == null || defaultEventDuration.isNegative() || defaultEventDuration.isZero()
                ? Duration.ofHours(1)
                : defaultEventDuration;
    }

    public UserContext create(UserIdentity userId, String conversationId) {
        return new UserContext(
                userId,
                defaultTimezone,
                defaultLanguage,
                defaultConfirmationPreference,
                defaultEventDuration,
                conversationId
        );
    }
}
