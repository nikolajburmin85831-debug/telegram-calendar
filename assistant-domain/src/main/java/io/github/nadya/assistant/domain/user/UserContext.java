package io.github.nadya.assistant.domain.user;

import io.github.nadya.assistant.domain.common.Timezone;

import java.time.Duration;
import java.util.Objects;

public record UserContext(
        UserIdentity userId,
        Timezone preferredTimezone,
        String preferredLanguage,
        ConfirmationPreference confirmationPreference,
        Duration defaultEventDuration,
        String activeConversationId
) {

    public UserContext {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(preferredTimezone, "preferredTimezone must not be null");
        preferredLanguage = preferredLanguage == null || preferredLanguage.isBlank() ? "ru" : preferredLanguage;
        confirmationPreference = confirmationPreference == null
                ? ConfirmationPreference.AUTO_EXECUTE
                : confirmationPreference;
        defaultEventDuration = defaultEventDuration == null || defaultEventDuration.isNegative() || defaultEventDuration.isZero()
                ? Duration.ofHours(1)
                : defaultEventDuration;
    }

    public static UserContext defaultFor(UserIdentity userId, String conversationId) {
        return new UserContext(
                userId,
                new Timezone("Europe/Moscow"),
                "ru",
                ConfirmationPreference.AUTO_EXECUTE,
                Duration.ofHours(1),
                conversationId
        );
    }

    public UserContext withActiveConversationId(String conversationId) {
        return new UserContext(
                userId,
                preferredTimezone,
                preferredLanguage,
                confirmationPreference,
                defaultEventDuration,
                conversationId
        );
    }
}
