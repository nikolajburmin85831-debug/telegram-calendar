package io.github.nadya.assistant.adapter.in.telegram.polling.mapper;

import io.github.nadya.assistant.adapter.in.telegram.polling.dto.TelegramUpdateDto;
import io.github.nadya.assistant.domain.common.ChannelType;
import io.github.nadya.assistant.domain.conversation.IncomingUserMessage;
import io.github.nadya.assistant.domain.user.UserIdentity;

import java.time.Instant;
import java.util.Optional;

public final class TelegramUpdateMapper {

    public Optional<IncomingUserMessage> map(TelegramUpdateDto update) {
        if (update == null || update.message() == null || update.message().from() == null || update.message().chat() == null) {
            return Optional.empty();
        }
        if (update.message().text() == null || update.message().text().isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new IncomingUserMessage(
                "telegram-update-" + update.updateId(),
                String.valueOf(update.message().messageId()),
                new UserIdentity("telegram-user:" + update.message().from().id()),
                ChannelType.TELEGRAM,
                "telegram-chat:" + update.message().chat().id(),
                update.message().text(),
                update.message().date() == null ? Instant.now() : update.message().date()
        ));
    }
}
