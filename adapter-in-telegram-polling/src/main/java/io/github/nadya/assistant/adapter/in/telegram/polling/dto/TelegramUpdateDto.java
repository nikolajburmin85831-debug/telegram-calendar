package io.github.nadya.assistant.adapter.in.telegram.polling.dto;

import java.time.Instant;

public record TelegramUpdateDto(long updateId, TelegramMessageDto message) {

    public record TelegramMessageDto(long messageId, TelegramUserDto from, TelegramChatDto chat, String text, Instant date) {
    }

    public record TelegramUserDto(long id, String username, String languageCode) {
    }

    public record TelegramChatDto(long id, String type) {
    }
}
