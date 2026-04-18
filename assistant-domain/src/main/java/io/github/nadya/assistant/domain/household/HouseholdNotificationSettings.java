package io.github.nadya.assistant.domain.household;

import io.github.nadya.assistant.domain.user.UserIdentity;

import java.util.LinkedHashMap;
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

    public Optional<HouseholdMember> findByMemberId(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(members.get(memberId.trim()));
    }
}
