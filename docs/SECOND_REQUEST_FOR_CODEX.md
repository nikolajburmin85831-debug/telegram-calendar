# SECOND_REQUEST_FOR_CODEX.md

## Purpose

Use this request **after** Codex has already created the initial Gradle multi-module skeleton from:

- `docs/assistant_core_architecture_spec_v2.md`
- `docs/IMPLEMENTATION_SCOPE_V1_FOR_CODEX.md`

This second request moves the project from **architectural skeleton** to the **first real working vertical slice**.

The goal is not full production completeness.  
The goal is to make the first end-to-end flow concrete while preserving the architecture.

---

## Request to Codex

Read the existing project structure and the architecture documents:

- `docs/assistant_core_architecture_spec_v2.md`
- `docs/IMPLEMENTATION_SCOPE_V1_FOR_CODEX.md`

Then continue implementation from the current skeleton.

Your task now is to implement the **first working vertical slice** of the assistant while preserving the module boundaries and hexagonal architecture.

---

## Main target

Implement the first usable end-to-end scenario:

1. Telegram polling receives a text message
2. the message is normalized into the internal incoming message model
3. the application layer processes it
4. the interpreter returns an internal intent interpretation
5. if the intent is understood and enough data exists, the system creates a `CalendarEventDraft`
6. the calendar port is invoked
7. the notification port sends a response back to Telegram
8. persistence stores conversation/context/idempotency state as needed

---

## What to implement now

### 1. Finish the main message-handling flow
Take the skeleton flow and make it concrete enough to work for the first scenario.

Expected behavior:
- handle one plain text Telegram message
- process it through the application layer
- return either:
  - success path for event creation
  - clarification path if required data is missing

Do not try to support many commands yet.
Focus on one clean path.

---

### 2. Define and stabilize the first supported intent
Support one main intent:

- create calendar event / reminder from a natural-language message

At minimum, the internal interpretation should support fields like:
- title
- date
- time or all-day
- timezone if available
- confidence
- missing or ambiguous fields

Use internal canonical models only.

---

### 3. Make the application layer actually orchestrate the flow
Implement the application logic so that:
- incoming message handling is real
- intent routing is real
- clarification decision is real
- execution decision is real
- calendar event creation path is real at the application level

This can still be lightweight, but it should no longer be only placeholder skeleton code.

---

### 4. Improve the Gemini adapter
Replace the pure placeholder with a more realistic implementation boundary.

Acceptable options:
- a temporary deterministic stub that maps specific sample inputs to internal interpretations
- or a minimal real Gemini integration scaffold if safe and clean

Priority:
- preserve adapter boundaries
- keep provider-specific details isolated
- make the rest of the system callable and testable

Do not leak provider DTOs outside the adapter.

---

### 5. Improve the Google Calendar adapter
Replace the pure placeholder with a more realistic first version.

Acceptable options:
- a stub that simulates event creation and returns a plausible internal result
- or a minimal real Google Calendar integration scaffold

Priority:
- keep `CalendarEventDraft` as the input
- hide provider details behind the adapter
- make the flow realistic enough to exercise the port boundary

Do not leak provider SDK types outside the adapter.

---

### 6. Make persistence adapters usable for the first flow
Ensure in-memory implementations actually support:
- user context lookup/save
- conversation state lookup/save
- idempotency check/store

These do not need to be production-grade, but they should be sufficient to support the first flow cleanly.

---

### 7. Make Telegram notification adapter usable
Implement the first real response path so the system can send back:
- success confirmation
- clarification message
- failure message

Formatting can stay simple.

---

### 8. Add configuration scaffolding in app-ec2
Make `app-ec2` usable as the runtime shell for local development and early EC2 deployment.

Expected:
- Spring Boot application entrypoint
- module wiring
- config placeholders
- environment/config keys for Telegram and future external services
- minimal startup arrangement for polling

---

### 9. Add basic tests where useful
You do not need broad test coverage, but add focused tests where they give clear value.

Best candidates:
- application flow tests
- clarification decision tests
- intent routing tests
- in-memory persistence behavior tests

Prefer a few targeted tests over many shallow ones.

---

## Constraints

### Must preserve
- module boundaries
- ports-and-adapters structure
- provider isolation
- runtime shell separation
- EC2-first, Lambda-ready design

### Must not introduce
- Lambda runtime code
- webhook support
- OpenClaw integration
- business logic inside polling adapter
- business logic inside Spring Boot bootstrap
- provider-specific models inside domain/application
- giant god-service that owns everything

---

## Preferred implementation strategy

If there is a choice, prefer this order:

1. make the application flow coherent
2. make adapters realistic enough for the flow
3. keep code readable and modular
4. leave clean TODO markers for unfinished provider-specific details
5. avoid premature production hardening

---

## Acceptance criteria for this step

This second step is acceptable if:

1. the first end-to-end flow is visible in code
2. message handling is more than just a skeleton
3. the application layer clearly coordinates interpretation, clarification, execution, and response
4. persistence adapters are usable for state/context/idempotency
5. Telegram polling and outbound notification paths are both implemented enough to understand the flow
6. adapter boundaries remain intact
7. the code is still a good base for later webhook/Lambda work

---

## Expected deliverables

After implementation, report back with:

1. which files/classes were added or significantly changed
2. what is now actually working end-to-end
3. what is still stubbed or simulated
4. any architectural tradeoffs made
5. what the recommended third step should be

---

## Final instruction

Do not broaden scope.

Implement the **smallest clean working vertical slice** for:
- Telegram polling input
- internal intent processing
- calendar-event path
- Telegram response output

Prefer a narrow working flow with correct architecture over a wide but messy implementation.
