# FOURTH_REQUEST_FOR_CODEX.md

## Purpose

Use this request **after** the third iteration is complete and the project already has:

- a Gradle multi-module architecture
- a working first vertical slice
- multi-turn clarification and confirmation continuation
- persisted pending action state
- deterministic continuation merge logic
- stubbed external integrations behind proper port boundaries

This fourth step should move the project from a **stateful conversational foundation** toward a **real usable bot runtime**, while still preserving architecture and keeping scope controlled.

The priorities for this step are intentionally ordered:

1. real Telegram round-trip
2. continuation and enrichment polish
3. minimal real Gemini adapter
4. minimal real Google Calendar adapter

Do **not** broaden scope into Lambda, webhook, OpenClaw, or advanced production hardening.

---

## Source of truth

Use these files as architectural and implementation context:

- `docs/assistant_core_architecture_spec_v3.md`
- `docs/IMPLEMENTATION_SCOPE_V1_FOR_CODEX.md`
- `docs/SECOND_REQUEST_FOR_CODEX.md`
- `docs/THIRD_REQUEST_FOR_CODEX.md`

Also inspect the current repository state before making changes.

---

## Main target

Implement the fourth iteration so the assistant becomes a **real Telegram bot in practice**, while keeping core logic and boundaries intact.

At the end of this step, the preferred outcome is:

- real Telegram polling receives actual updates
- real Telegram outbound messaging sends actual replies
- existing success / clarification / confirmation / cancel flows still work
- continuation handling is slightly more capable
- Gemini adapter can optionally use a minimal real integration
- Google Calendar adapter can optionally use a minimal real integration
- architecture remains clean and stable

The highest priority is **real Telegram ingress and egress**, not provider completeness elsewhere.

---

## What to implement now

### 1. Replace stub Telegram polling with a real polling adapter
Implement a real Telegram polling client using Telegram Bot API `getUpdates`.

Expected behavior:
- fetch real updates from Telegram
- track update offsets correctly
- normalize incoming Telegram messages into the internal incoming message model
- preserve idempotency behavior
- keep transport details inside the Telegram polling adapter

This should make the bot able to receive real user messages in Telegram.

Do not push Telegram DTOs into application or domain layers.

---

### 2. Replace stub Telegram notification sending with a real outbound adapter
Implement real outbound Telegram messaging using `sendMessage`.

Expected behavior:
- send success replies
- send clarification prompts
- send confirmation prompts
- send cancel/failure replies
- keep channel formatting simple

This should make the bot able to visibly answer in Telegram.

Keep Telegram-specific request/response details inside the adapter.

---

### 3. Polish multi-turn continuation and enrichment
Improve the continuation flow without turning this step into a large NLP project.

Target improvements:
- support follow-up messages that can fill more than one missing field, for example:
  - "завтра в 10"
  - "в 10 утра"
  - "весь день"
- allow the assistant to complete more than one pending gap when a follow-up provides enough information
- handle a clearly new unrelated request during pending flow in a controlled way

For the last case, choose one explicit strategy and apply it consistently:
- either treat it as interruption and reset current pending flow
- or reject it and ask user to finish/cancel the current flow first

Do not make this behavior ambiguous.

---

### 4. Add a minimal real Gemini adapter path
Move the Gemini adapter from pure heuristic stub toward a real adapter.

Acceptable approach:
- keep the deterministic stub path for tests and local fallback
- add an optional real Gemini integration path behind configuration
- keep provider DTOs inside the adapter
- map the response into the existing canonical internal interpretation

Requirements:
- application/domain must still not depend on Gemini response shapes
- tests should not require real external calls
- if the real path is incomplete, leave clean TODOs rather than leaking provider specifics

This step is about creating the real adapter boundary, not about perfect prompt engineering.

---

### 5. Add a minimal real Google Calendar adapter path
Move the Google Calendar adapter from pure stub toward a real adapter.

Acceptable approach:
- keep stub behavior available for tests/local fallback
- add an optional real integration path behind configuration
- use internal `CalendarEventDraft` as the only application-facing input
- return internal result/reference objects
- keep Google-specific SDK models or DTOs inside the adapter

If full OAuth completion is too large for this step, create a clean scaffold with clear TODO boundaries.

Priority remains lower than Telegram and continuation polish.

---

### 6. Strengthen configuration and runtime wiring in app-ec2
Update `app-ec2` so it works as the real runtime shell for local development and early EC2 deployment.

Expected:
- clear config properties for Telegram, Gemini, and Google integrations
- separation of stub vs real integration modes where needed
- startup wiring that preserves existing architecture
- no business logic in bootstrap/config classes

The runtime should be understandable and practical to launch.

---

### 7. Preserve and extend tests
Update or add focused tests to preserve confidence while introducing real adapter paths.

Best candidates:
- application continuation tests
- tests ensuring adapter boundaries remain intact
- tests for stub/fallback behavior
- tests for new enrichment handling
- tests for idempotency with real polling offset handling where feasible at unit level

Do not overinvest in integration tests unless they are small and valuable.

---

## Constraints

### Must preserve
- hexagonal architecture
- module boundaries
- provider isolation
- runtime shell separation
- EC2-first, Lambda-ready design
- continuation logic in application/core layers
- external state behind ports

### Must not introduce
- Lambda runtime
- webhook support
- OpenClaw integration
- Telegram DTO leakage into application/domain
- Gemini provider DTO leakage outside Gemini adapter
- Google provider type leakage outside calendar adapter
- large production infrastructure changes
- giant orchestration class
- broad feature expansion unrelated to the stated priorities

---

## Recommended implementation order

Follow this order unless there is a strong reason not to:

1. real Telegram polling
2. real Telegram sendMessage
3. continuation/enrichment polish
4. minimal real Gemini path
5. minimal real Google Calendar path
6. supporting tests and config cleanup

This ordering is important.

The goal is to get a real conversational bot in Telegram before spending effort on deeper provider realism elsewhere.

---

## Acceptance criteria

This fourth step is acceptable if:

1. the bot can receive real Telegram messages through polling
2. the bot can send real Telegram replies
3. current conversational flows still work with real Telegram round-trip
4. continuation handling is improved for richer follow-up input
5. architecture remains intact and adapters remain isolated
6. Gemini has at least a clean optional real integration path
7. Google Calendar has at least a clean optional real integration path or scaffold
8. the codebase remains a strong foundation for future webhook/Lambda work

---

## Expected deliverables

After implementation, report back with:

1. which files/classes were added or significantly changed
2. which parts now use real Telegram integration
3. what continuation/enrichment improvements were added
4. what is still stubbed or fallback-only
5. whether Gemini real path is implemented or scaffolded
6. whether Google Calendar real path is implemented or scaffolded
7. any architectural tradeoffs made
8. what the recommended fifth step should be

---

## Final instruction

Do not drift into a broad “make everything real” rewrite.

This step is specifically about:
- making Telegram real
- polishing continuation
- preparing real Gemini
- preparing real Google Calendar

Preserve the architecture first.
