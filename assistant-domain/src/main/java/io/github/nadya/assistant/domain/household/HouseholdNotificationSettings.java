package io.github.nadya.assistant.domain.household;

import io.github.nadya.assistant.domain.user.UserIdentity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record HouseholdNotificationSettings(
        boolean enabled,
        Map<String, HouseholdMember> members
) {

    public HouseholdNotificationSettings {
        members = Map.copyOf(members == null ? Map.of() : new LinkedHashMap<>(members));
    }

    public static HouseholdNotificationSettings disabled() {
        return new HouseholdNotificationSettings(false, Map.of());
    }

    public Optional<HouseholdMember> findByUserId(UserIdentity userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return members.values().stream()
                .filter(member -> member.userId().value().equals(userId.value()))
                .findFirst();
    }

    public Optional<HouseholdMember> findInitiator(UserIdentity userId, String conversationId) {
        Optional<HouseholdMember> byUserId = findByUserId(userId);
        if (byUserId.isPresent()) {
            return byUserId;
        }
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }

        List<HouseholdMember> byConversationId = members.values().stream()
                .filter(member -> member.conversationIds().contains(conversationId))
                .toList();
        if (byConversationId.size() == 1) {
            return Optional.of(byConversationId.get(0));
        }
        return Optional.empty();
    }

    public Optional<HouseholdMember> findByMemberId(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(members.get(memberId.trim()));
    }
}
