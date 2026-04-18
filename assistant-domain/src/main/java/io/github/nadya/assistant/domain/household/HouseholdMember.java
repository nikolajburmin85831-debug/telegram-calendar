package io.github.nadya.assistant.domain.household;

import io.github.nadya.assistant.domain.user.UserIdentity;

import java.util.List;
import java.util.Objects;

public record HouseholdMember(
        String memberId,
        String displayName,
        UserIdentity userId,
        List<String> conversationIds,
        List<String> notifyMemberIds
) {

    public HouseholdMember {
        memberId = Objects.requireNonNullElse(memberId, "").trim();
        displayName = Objects.requireNonNullElse(displayName, "").trim();
        Objects.requireNonNull(userId, "userId must not be null");
        conversationIds = List.copyOf(conversationIds == null ? List.of() : conversationIds);
        notifyMemberIds = List.copyOf(notifyMemberIds == null ? List.of() : notifyMemberIds);
        if (memberId.isBlank()) {
            throw new IllegalArgumentException("memberId must not be blank");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
    }
}
