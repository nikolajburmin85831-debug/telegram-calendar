package io.github.nadya.assistant.app.ec2.config.properties;

import io.github.nadya.assistant.domain.household.HouseholdMember;
import io.github.nadya.assistant.domain.household.HouseholdNotificationSettings;
import io.github.nadya.assistant.domain.user.UserIdentity;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "assistant.household")
public record AssistantHouseholdProperties(
        boolean enabled,
        Member me,
        Member wife
) {

    public AssistantHouseholdProperties {
        me = me == null ? Member.empty() : me;
        wife = wife == null ? Member.empty() : wife;
    }

    public HouseholdNotificationSettings toSettings() {
        Map<String, HouseholdMember> members = new LinkedHashMap<>();
        addMember(members, "me", "Я", me);
        addMember(members, "wife", "Жена", wife);
        return new HouseholdNotificationSettings(enabled, members);
    }

    private void addMember(Map<String, HouseholdMember> members, String memberId, String defaultDisplayName, Member member) {
        String normalizedUserId = member.normalizedUserId();
        List<String> normalizedConversationIds = member.normalizedConversationIds();
        if (normalizedUserId.isBlank() && normalizedConversationIds.isEmpty()) {
            return;
        }
        members.put(memberId, new HouseholdMember(
                memberId,
                member.displayName(defaultDisplayName),
                new UserIdentity(normalizedUserId.isBlank() ? "household-member:" + memberId : normalizedUserId),
                normalizedConversationIds,
                member.normalizedNotifyMembers()
        ));
    }

    public record Member(
            String displayName,
            String telegramUserId,
            List<String> chatIds,
            List<String> notifyMembers
    ) {
        public Member {
            displayName = displayName == null ? "" : displayName.trim();
            telegramUserId = telegramUserId == null ? "" : telegramUserId.trim();
            chatIds = List.copyOf(chatIds == null ? List.of() : chatIds);
            notifyMembers = List.copyOf(notifyMembers == null ? List.of() : notifyMembers);
        }

        static Member empty() {
            return new Member("", "", List.of(), List.of());
        }

        String displayName(String fallback) {
            return displayName.isBlank() ? fallback : displayName;
        }

        String normalizedUserId() {
            if (telegramUserId.isBlank()) {
                return "";
            }
            return telegramUserId.startsWith("telegram-user:")
                    ? telegramUserId
                    : "telegram-user:" + telegramUserId;
        }

        List<String> normalizedConversationIds() {
            return chatIds.stream()
                    .map(value -> value == null ? "" : value.trim())
                    .filter(value -> !value.isBlank())
                    .map(value -> value.startsWith("telegram-chat:") ? value : "telegram-chat:" + value)
                    .distinct()
                    .toList();
        }

        List<String> normalizedNotifyMembers() {
            return notifyMembers.stream()
                    .map(value -> value == null ? "" : value.trim())
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }
    }
}
