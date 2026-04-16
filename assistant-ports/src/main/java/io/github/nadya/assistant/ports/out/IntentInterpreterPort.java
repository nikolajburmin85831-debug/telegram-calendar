package io.github.nadya.assistant.ports.out;

import io.github.nadya.assistant.domain.intent.IntentInterpretation;

public interface IntentInterpreterPort {

    IntentInterpretation interpret(IntentInterpretationRequest request);
}
