# THIRD_REQUEST_FOR_CODEX.md

## Purpose

Use this request **after** the second iteration is complete and the project already has:

- a Gradle multi-module skeleton
- a first working vertical slice
- Telegram polling input flow
- internal message normalization
- application-layer orchestration
- success path for calendar-event creation
- clarification path with persisted conversation state
- in-memory persistence for context, conversation state, and idempotency
- stubbed external provider integrations behind port boundaries

This third step should turn the assistant from a single-turn vertical slice into a **true multi-turn conversational assistant foundation**.

The main focus is:

- resuming pending clarification flows
- resuming pending confirmation flows
- merging follow-up user input into the pending action
- preserving architecture
- keeping external integrations stubbed unless required for clean progress

Do **not** broaden scope into Lambda, webhook, OpenClaw, or full real provider integrations.

---

## Source of truth

Use these files as architectural and implementation context:

- `docs/assistant_core_architecture_spec_v3.md`
- `docs/IMPLEMENTATION_SCOPE_V1_FOR_CODEX.md`
- `docs/SECOND_REQUEST_FOR_CODEX.md`

Also inspect the current repository state before making changes.

---

## Main target

Implement the **pending clarification / confirmation resume flow**.

The assistant must be able to treat a follow-up user message as a continuation of a previous pending interaction rather than always as a brand-new request.

At the end of this step, the system should support this style of behavior:

### Clarification example
User:
- "Напомни завтра позвонить маме"

System:
- asks for missing time
- stores pending clarification state

User:
- "в 10:00"

System:
- recognizes this as continuation of the pending flow
- merges the missing time into the pending event intent/draft
- executes the action
- sends success confirmation
- clears the pending state

### Confirmation example
User:
- asks for an action that the policy marks as requiring confirmation

System:
- asks for confirmation
- stores pending confirmation state

User:
- "да"

System:
- resumes the pending action
- executes it
- sends success confirmation
- clears the pending confirmation

Also support negative/cancel-style outcomes where appropriate.

---

## What to implement now

### 1. Implement resume logic for pending clarification flows
The application layer must detect when an incoming message belongs to an existing pending clarification flow.

Expected behavior:
- load conversation state
- detect that the user is currently in a pending clarification state
- interpret the new message in the context of the missing field(s)
- merge the new data into the pending action
- re-run execution decision
- either:
  - execute successfully, or
  - ask for another clarification if still incomplete

This must happen in the application/core layers, not in the Telegram adapter.

---

### 2. Implement resume logic for pending confirmation flows
The application layer must detect when an incoming message is answering a pending confirmation.

Expected behavior:
- load pending confirmation
- recognize simple positive and negative responses
- on positive:
  - resume pending action
  - execute it
  - clear pending confirmation state
- on negative or cancel:
  - cancel the pending action
  - clear pending state
  - send an appropriate response

Keep this first version simple and deterministic.

---

### 3. Introduce a clear pending-action representation if needed
If the current model is insufficient, add or refine internal models needed for multi-turn continuation.

Possible concepts:
- pending action payload
- pending interpreted intent
- pending calendar draft
- missing field list
- asked clarification metadata
- pending confirmation payload

Design goal:
- the system must have enough structured state to continue the conversation cleanly
- do not store provider-specific data in pending state

---

### 4. Define and implement merge behavior
When a user answers a clarification, the system must merge the new information into the existing pending state.

Expected examples:
- missing time gets filled in
- missing date gets filled in
- confirmation response resolves the pending confirmation
- unrelated or ambiguous follow-up should not silently corrupt the pending action

You should define a simple, explicit merge strategy in code.

Prefer predictable behavior over overly clever interpretation.

---

### 5. Handle cancellation and invalid follow-up responses
Support the basic conversational control cases needed for pending flows.

At minimum, handle:
- positive confirmation like "да"
- negative confirmation like "нет"
- cancel intent like "отмена"
- unhelpful follow-up like an unrelated or still ambiguous message

The system should:
- either resume correctly
- ask again clearly
- or cancel/reset safely

Do not allow the assistant to drift into inconsistent pending state.

---

### 6. Refine conversation-state model if needed
If the current conversation state abstraction is too weak, improve it.

The state model should be explicit enough to distinguish at least:
- idle
- awaiting clarification
- awaiting confirmation
- executing
- completed
- failed or cancelled

The model should support identifying what exactly is pending and what information is still required.

---

### 7. Keep persistence aligned with the new flow
Update persistence adapters as needed so that pending continuation actually works.

The in-memory persistence layer should now be able to store and retrieve:
- pending clarification state
- pending confirmation state
- sufficient data to resume an interrupted flow
- cleared state after completion/cancellation

Keep it simple, but make it correct for the flow.

---

### 8. Add focused tests for multi-turn behavior
This step should include tests that prove the assistant can continue a conversation.

Best test targets:
- clarification resume flow
- confirmation resume flow
- cancel flow
- repeated ambiguous follow-up
- state clearing after success
- state clearing after cancel

Prefer small high-value tests over broad superficial coverage.

---

## Constraints

### Must preserve
- hexagonal architecture
- module boundaries
- provider isolation
- separation of runtime shell from core
- EC2-first, Lambda-ready design
- external integrations behind ports

### Must not introduce
- Lambda runtime
- webhook support
- OpenClaw integration
- business logic in polling adapter
- provider DTO leakage into application/domain
- giant orchestration class that collapses multiple responsibilities
- overcomplicated NLP for follow-up messages in this step

---

## Recommended implementation strategy

Prefer this order:

1. model pending continuation cleanly
2. implement clarification resume flow
3. implement confirmation resume flow
4. implement cancellation handling
5. add focused tests
6. only then consider polishing stubs if needed

---

## Acceptance criteria

This third step is acceptable if:

1. a follow-up message can complete a previously clarified flow
2. a follow-up confirmation response can execute or cancel a pending action
3. pending state is persisted and later cleared correctly
4. the application layer clearly owns continuation behavior
5. adapters remain thin and architecture remains intact
6. the project is now a real multi-turn assistant foundation, not just a single-turn flow
7. the code remains a good base for later real provider integrations and eventual webhook/Lambda evolution

---

## Expected deliverables

After implementation, report back with:

1. which files/classes were added or significantly changed
2. what multi-turn flows now work end-to-end
3. what is still stubbed or simplified
4. what merge strategy was implemented
5. any architectural tradeoffs made
6. what the recommended fourth step should be

---

## Final instruction

Do not switch focus to real provider integrations yet unless absolutely necessary for clean progress.

This step is about making the assistant conversationally stateful.

Implement the **smallest clean multi-turn continuation flow** that:
- resumes clarification
- resumes confirmation
- supports cancel
- preserves architecture
