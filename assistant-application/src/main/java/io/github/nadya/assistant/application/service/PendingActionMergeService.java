package io.github.nadya.assistant.application.service;

import io.github.nadya.assistant.domain.common.ConfidenceScore;
import io.github.nadya.assistant.domain.conversation.ClarificationRequest;
import io.github.nadya.assistant.domain.conversation.PendingAction;
import io.github.nadya.assistant.domain.intent.AssistantIntent;
import io.github.nadya.assistant.domain.intent.IntentInterpretation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PendingActionMergeService {

    public IntentInterpretation merge(
            PendingAction pendingAction,
            ClarificationRequest clarificationRequest,
            IntentInterpretation followUpInterpretation
    ) {
        IntentInterpretation baseInterpretation = pendingAction.interpretation();
        LinkedHashMap<String, String> mergedEntities = new LinkedHashMap<>(baseInterpretation.assistantIntent().entities());
        Set<String> allowedKeys = allowedEntityKeys(baseInterpretation, clarificationRequest);

        for (Map.Entry<String, String> entry : followUpInterpretation.assistantIntent().entities().entrySet()) {
            if (!allowedKeys.contains(entry.getKey())) {
                continue;
            }
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            mergedEntities.put(entry.getKey(), entry.getValue());
        }

        normalizeTiming(mergedEntities, followUpInterpretation.assistantIntent().entities());

        List<String> missingFields = computeMissingFields(mergedEntities);
        List<String> ambiguityMarkers = computeAmbiguityMarkers(
                baseInterpretation.ambiguityMarkers(),
                followUpInterpretation.ambiguityMarkers(),
                mergedEntities
        );
        boolean safeToExecute = missingFields.isEmpty() && ambiguityMarkers.isEmpty();
        double confidence = Math.max(
                baseInterpretation.confidenceScore().value(),
                followUpInterpretation.confidenceScore().value()
        );

        return new IntentInterpretation(
                new AssistantIntent(baseInterpretation.intentType(), mergedEntities),
                new ConfidenceScore(confidence),
                ambiguityMarkers,
                missingFields,
                safeToExecute
        );
    }

    private Set<String> allowedEntityKeys(
            IntentInterpretation baseInterpretation,
            ClarificationRequest clarificationRequest
    ) {
        LinkedHashSet<String> allowedKeys = new LinkedHashSet<>();
        LinkedHashSet<String> unresolvedMarkers = new LinkedHashSet<>(baseInterpretation.missingFields());
        unresolvedMarkers.addAll(baseInterpretation.ambiguityMarkers());
        if (clarificationRequest != null) {
            if (clarificationRequest.reason() != null && !clarificationRequest.reason().isBlank()) {
                unresolvedMarkers.add(clarificationRequest.reason());
            }
            unresolvedMarkers.addAll(clarificationRequest.missingFields());
        }

        if (!unresolvedMarkers.isEmpty()) {
            if (unresolvedMarkers.contains("title")) {
                allowedKeys.add("title");
            }
            allowedKeys.add("startDate");
            allowedKeys.add("startTime");
            allowedKeys.add("allDay");
        }
        return allowedKeys;
    }

    private void normalizeTiming(Map<String, String> mergedEntities, Map<String, String> followUpEntities) {
        if (followUpEntities.containsKey("startTime") && !followUpEntities.getOrDefault("startTime", "").isBlank()) {
            mergedEntities.put("allDay", "false");
        }

        if (Boolean.parseBoolean(mergedEntities.getOrDefault("allDay", "false"))) {
            mergedEntities.remove("startTime");
        }
    }

    private List<String> computeMissingFields(Map<String, String> entities) {
        ArrayList<String> missingFields = new ArrayList<>();
        if (entities.getOrDefault("title", "").isBlank()) {
            missingFields.add("title");
        }
        if (entities.getOrDefault("startDate", "").isBlank()) {
            missingFields.add("date");
        }
        boolean allDay = Boolean.parseBoolean(entities.getOrDefault("allDay", "false"));
        if (!allDay && entities.getOrDefault("startTime", "").isBlank()) {
            missingFields.add("time");
        }
        return List.copyOf(missingFields);
    }

    private List<String> computeAmbiguityMarkers(
            List<String> baseAmbiguityMarkers,
            List<String> followUpAmbiguityMarkers,
            Map<String, String> entities
    ) {
        LinkedHashSet<String> remainingAmbiguity = new LinkedHashSet<>();
        LinkedHashSet<String> candidates = new LinkedHashSet<>(baseAmbiguityMarkers);
        candidates.addAll(followUpAmbiguityMarkers);

        boolean hasResolvedTime = Boolean.parseBoolean(entities.getOrDefault("allDay", "false"))
                || !entities.getOrDefault("startTime", "").isBlank();

        for (String marker : candidates) {
            if ("time_is_range".equals(marker) && !hasResolvedTime) {
                remainingAmbiguity.add(marker);
            }
        }

        return List.copyOf(remainingAmbiguity);
    }
}
