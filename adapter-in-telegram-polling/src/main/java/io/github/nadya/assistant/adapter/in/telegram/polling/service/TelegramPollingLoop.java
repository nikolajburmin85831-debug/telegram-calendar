package io.github.nadya.assistant.adapter.in.telegram.polling.service;

import io.github.nadya.assistant.adapter.in.telegram.polling.client.TelegramPollingClient;
import io.github.nadya.assistant.adapter.in.telegram.polling.config.TelegramPollingProperties;
import io.github.nadya.assistant.adapter.in.telegram.polling.dto.TelegramUpdateDto;
import io.github.nadya.assistant.adapter.in.telegram.polling.mapper.TelegramUpdateMapper;
import io.github.nadya.assistant.ports.in.HandleIncomingMessageUseCase;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class TelegramPollingLoop implements AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(TelegramPollingLoop.class.getName());

    private final TelegramPollingClient telegramPollingClient;
    private final TelegramUpdateMapper telegramUpdateMapper;
    private final HandleIncomingMessageUseCase handleIncomingMessageUseCase;
    private final TelegramPollingProperties properties;
    private final AtomicLong nextOffset = new AtomicLong(0L);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public TelegramPollingLoop(
            TelegramPollingClient telegramPollingClient,
            TelegramUpdateMapper telegramUpdateMapper,
            HandleIncomingMessageUseCase handleIncomingMessageUseCase,
            TelegramPollingProperties properties
    ) {
        this.telegramPollingClient = telegramPollingClient;
        this.telegramUpdateMapper = telegramUpdateMapper;
        this.handleIncomingMessageUseCase = handleIncomingMessageUseCase;
        this.properties = properties;
    }

    public void start() {
        if (!properties.enabled() || !started.compareAndSet(false, true)) {
            return;
        }

        executorService.scheduleWithFixedDelay(
                this::pollSafely,
                0L,
                properties.pollInterval().toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    public int pollOnce() {
        List<TelegramUpdateDto> updates = telegramPollingClient.getUpdates(nextOffset.get(), properties.limit()).stream()
                .sorted(Comparator.comparingLong(TelegramUpdateDto::updateId))
                .toList();
        for (TelegramUpdateDto update : updates) {
            telegramUpdateMapper.map(update).ifPresent(handleIncomingMessageUseCase::handle);
        }

        updates.stream()
                .map(TelegramUpdateDto::updateId)
                .max(Comparator.naturalOrder())
                .ifPresent(maxUpdateId -> nextOffset.set(maxUpdateId + 1));

        return updates.size();
    }

    private void pollSafely() {
        try {
            pollOnce();
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Telegram polling iteration failed", exception);
        }
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }
}
