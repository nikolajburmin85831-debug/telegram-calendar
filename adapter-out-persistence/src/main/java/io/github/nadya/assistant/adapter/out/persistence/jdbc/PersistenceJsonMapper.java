package io.github.nadya.assistant.adapter.out.persistence.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class PersistenceJsonMapper {

    private final ObjectMapper objectMapper;

    public PersistenceJsonMapper() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persistence payload could not be serialized", exception);
        }
    }

    public <T> T read(String payload, Class<T> targetType) {
        try {
            return objectMapper.readValue(payload, targetType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persistence payload could not be deserialized", exception);
        }
    }
}
