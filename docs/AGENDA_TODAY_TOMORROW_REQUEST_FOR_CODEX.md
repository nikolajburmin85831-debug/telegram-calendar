# AGENDA_TODAY_TOMORROW_REQUEST_FOR_CODEX.md

## Purpose

Use this request after the combined guard + household-notification step is complete.

The project already has:
- real Telegram polling and replies
- real/stub Gemini path
- real/stub Google Calendar path
- multi-turn clarification and confirmation
- durable persistence
- profile-based config split
- restart-safe runtime behavior
- deterministic execution guard before calendar side effects
- household notifications after successful side effects

This step intentionally focuses on only one new product capability:

1. list events for today
2. list events for tomorrow

This is a read-only calendar capability.
Do not broaden scope into update/reschedule, cancel/delete, audio, Lambda, webhook, OpenClaw, or unrelated integrations.

---

## Source of truth

Use these files as architectural and implementation context:

- `docs/assistant_core_architecture_spec_v3.md`
- `docs/IMPLEMENTATION_SCOPE_V1_FOR_CODEX.md`
- `docs/SECOND_REQUEST_FOR_CODEX.md`
- `docs/THIRD_REQUEST_FOR_CODEX.md`
- `docs/FOURTH_REQUEST_FOR_CODEX.md`
- `docs/FIFTH_REQUEST_FOR_CODEX_UPDATED.md`
- `docs/GUARD_AND_HOUSEHOLD_REQUEST_FOR_CODEX.md`

Also inspect the current repository state before making changes.

---

## Main target

Implement a simple but useful read-only agenda flow so the assistant can answer:

- “What do I have today?”
- “What do I have tomorrow?”
- “Какие у меня планы сегодня?”
- “Что у меня завтра?”

The system should:
- recognize the agenda intent
- determine the correct date range
- retrieve events through `CalendarPort`
- return a concise user-facing agenda summary in Telegram

This step should make the assistant more useful day to day without adding new destructive or high-risk flows.

---

## What to implement now

### 1. Add a dedicated agenda read use case
Introduce a clear application-level read flow for agenda/day view.

Suggested concepts:
- `GetAgendaHandler`
- `ListAgendaHandler`
- `AgendaQuery`
- `AgendaResult`

Final naming should align with the current codebase.

The key requirement is that agenda becomes a first-class read use case, not a special case hidden inside some other handler.

---

### 2. Support minimum date ranges
Support at minimum:
- today
- tomorrow

Do not broaden scope yet into arbitrary weeks/months unless it is almost free to support.

---

### 3. Add an internal date-range model if needed
If useful, introduce an internal date-range or agenda-query model for read-only calendar access.

This should remain provider-agnostic.

Do not expose Google-specific query parameters outside the calendar adapter boundary.

---

### 4. Extend CalendarPort read capability
Add or extend the `CalendarPort` so it can retrieve events for a given date range.

Requirements:
- application/core uses internal query/result models
- provider-specific details remain inside the Google Calendar adapter
- no Google DTO leakage outside the adapter

The adapter may internally call the appropriate Google Calendar list/read APIs, but application/core should not know the provider details.

---

### 5. Keep agenda formatting simple
Return a concise and readable user-facing summary.

A simple output is enough, for example:
- date heading
- time
- title
- optional location if already easy

Do not overengineer formatting.

The goal is usefulness in Telegram, not rich rendering.

---

### 6. Handle empty agenda clearly
If there are no events in the requested range, respond explicitly and clearly.

Example patterns:
- “No events scheduled for today.”
- “Nothing is scheduled for tomorrow.”
- localized/project-style wording is acceptable

Do not return confusing empty output.

---

### 7. Reuse current interpretation architecture
Reuse the current text interpretation/date normalization approach.

Do not build a separate ad hoc parsing flow if the current architecture can already classify today/tomorrow requests.

This is a read-only capability, so it should remain simpler than destructive flows.

---

### 8. Keep clarification minimal
For this step, try to avoid unnecessary complexity.

For clearly recognized today/tomorrow requests, clarification should ideally not be needed.

If a request is too ambiguous, handle it cleanly, but do not broaden this step into general calendar search language.

---

## Constraints

### Must preserve
- hexagonal architecture
- module boundaries
- provider isolation
- current config split
- explicit `@ConfigurationProperties`
- existing clarification/confirmation model
- existing execution guard architecture
- current notification architecture

### Must not introduce
- Lambda runtime
- webhook support
- OpenClaw integration
- Google provider DTO leakage outside the adapter boundary
- giant god-service
- update/reschedule flow
- cancel/delete flow
- household notification extensions for agenda
- audio input

---

## Recommended implementation order

1. add agenda query/result model if needed
2. implement agenda application handler/use case
3. extend `CalendarPort` read capability
4. implement Google Calendar read path inside adapter
5. add agenda summary formatting
6. add focused tests

---

## Acceptance criteria

This step is acceptable if:

1. the assistant can answer agenda queries for today
2. the assistant can answer agenda queries for tomorrow
3. the agenda flow uses a `CalendarPort` read path
4. output is concise and readable in Telegram
5. empty-agenda cases are handled clearly
6. provider-specific details remain inside the adapter
7. architecture and boundaries remain intact

---

## Suggested test cases

Add focused tests for:
- today agenda with one or more events
- tomorrow agenda with one or more events
- empty today agenda
- empty tomorrow agenda
- correct date-range mapping for today
- correct date-range mapping for tomorrow
- provider-specific details remain inside adapter

---

## Expected deliverables

After implementation, report back with:

1. which files/classes were added or significantly changed
2. how the agenda read flow was modeled
3. how `CalendarPort` was extended
4. how the user-facing agenda summary is built
5. what remains intentionally simplified
6. what the recommended next step should be

---

## Final instruction

Do not jump to update/cancel/audio yet.

This step is specifically about adding a small, useful, read-only agenda capability:

- today
- tomorrow

Preserve the architecture first.
