package io.github.nadya.assistant.app.ec2.config.properties;

import io.github.nadya.assistant.domain.household.HouseholdMember;
import io.github.nadya.assistant.domain.household.HouseholdNotificationSettings;
import io.github.nadya.assistant.domain.user.UserIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantHouseholdPropertiesTest {

    @Test
    void toSettingsKeepsChatOnlyMemberUsingSyntheticUserId() {
        AssistantHouseholdProperties properties = new AssistantHouseholdProperties(
                true,
                new AssistantHouseholdProperties.Member(
                        "Me",
                        "42",
                        List.of("101"),
                        List.of()
                ),
                new AssistantHouseholdProperties.Member(
                        "Wife",
                        "",
                        List.of("202"),
                        List.of("me")
                )
        );

        HouseholdNotificationSettings settings = properties.toSettings();

        HouseholdMember wife = settings.findByMemberId("wife").orElseThrow();
        assertEquals("household-member:wife", wife.userId().value());
        assertEquals(List.of("telegram-chat:202"), wife.conversationIds());
        assertEquals(List.of("me"), wife.notifyMemberIds());
        assertTrue(settings.findInitiator(new UserIdentity("telegram-user:999"), "telegram-chat:202").isPresent());
    }
}
