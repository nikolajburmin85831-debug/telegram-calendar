# GUARD_AND_HOUSEHOLD_REQUEST_FOR_CODEX.md

## Purpose

Use this request after the fifth iteration is complete and before starting the audio-input step.

The project already has:
- real Telegram polling and replies
- real/stub Gemini path
- real/stub Google Calendar path
- multi-turn clarification and confirmation
- durable persistence
- profile-based config split
- restart-safe runtime behavior

This step combines two closely related improvements:

1. add a deterministic execution guard layer before side-effecting calendar actions
2. add household notifications, so that when one configured user (for example, the wife) successfully creates / reschedules / cancels an event, another configured user (for example, me) receives a Telegram notification

This combined step is intentional:
- the execution guard makes side effects safer
- household notifications should only happen after a side effect is safely approved and successfully completed

Do not broaden scope into audio, Lambda, webhook, OpenClaw, or new external integrations.

---

## Source of truth

Use these files as architectural and implementation context:

- `docs/assistant_core_architecture_spec_v3.md`
- `docs/IMPLEMENTATION_SCOPE_V1_FOR_CODEX.md`
- `docs/SECOND_REQUEST_FOR_CODEX.md`
- `docs/THIRD_REQUEST_FOR_CODEX.md`
- `docs/FOURTH_REQUEST_FOR_CODEX.md`
- `docs/FIFTH_REQUEST_FOR_CODEX_UPDATED.md`

Also inspect the current repository state before making changes.

---

## Main target

Implement a combined safety + household-notification step.

At the end of this step, the preferred behavior is:

### Safety / guard behavior
- interpreted or resumed event drafts do not go directly to `CalendarPort`
- a deterministic execution guard validates and classifies the action first
- malformed or unsafe drafts are rejected, downgraded, or routed to confirmation/clarification

### Household notification behavior
- if the wife successfully creates an event, I get a Telegram notification
- if the wife successfully reschedules an event, I get a Telegram notification
- if the wife successfully cancels an event, I get a Telegram notification
- if I perform an action myself, I do not receive a self-notification
- notifications are sent only after successful side effects
- no notification is sent if the action was blocked, failed, cancelled before execution, or never completed

---

# Part A — Deterministic execution guard

## 1. Introduce an explicit execution guard concept
Add a dedicated application/core component responsible for validating whether a calendar action is allowed to execute.

Suggested concepts:
- `ExecutionGuard`
- `CalendarExecutionGuard`
- `ExecutionGuardResult`
- `GuardViolation`
- `RiskLevel`

Keep final naming consistent with the current codebase.

The key requirement is that there is now an explicit code-level validation boundary before the calendar adapter is called.

---

## 2. Validate calendar draft shape deterministically
Before calling `CalendarPort`, validate the pending or freshly created `CalendarEventDraft`.

At minimum, validate:
- title is present and non-empty
- title length is within a safe bound
- start time/date structure is valid
- end or duration is valid if required
- timezone is acceptable
- event is not malformed
- all-day vs timed event is internally consistent

Do not rely on Gemini alone for any of these checks.

---

## 3. Add action-limiting policy
Guard against obviously risky or abusive event creation or update.

Suggested first-step limits:
- maximum event duration
- maximum scheduling horizon without confirmation
- no recurring events without explicit confirmation
- optional title/content sanity bounds
- optional rate/volume guard for too many events in one flow if relevant

Keep the first version simple and explicit.

The goal is not to build a perfect abuse engine, but to prevent clearly unsafe execution.

---

## 4. Separate interpretation from execution approval
Ensure the code clearly distinguishes:
- what the model/user input appears to mean
from
- what the system is willing to execute

The model may still return a valid interpretation, but the guard should be able to:
- allow execution
- require confirmation
- require clarification
- reject execution

This should integrate cleanly with the existing clarification/confirmation flow.

---

## 5. Add clear handling for suspicious or disallowed execution
If the guard rejects or downgrades execution, the user-facing response should remain clear.

Examples:
- “I need confirmation before creating that recurring event.”
- “I could not safely create that event because the date/time range is invalid.”
- “Please clarify the schedule.”

Do not fail silently.

---

## 6. Keep the guard in the application/core layer
Do not put this logic into:
- Telegram adapters
- Gemini adapters
- Google Calendar adapter
- Spring bootstrap/config code

This is a core/application-level decision boundary.

---

## 7. Add audit visibility for execution decisions
Where appropriate, ensure audit/logging can distinguish:
- interpreted intent
- guard decision
- final execution result

This does not need to become a full observability overhaul, but guard outcomes should be visible in code and diagnostics.

---

# Part B — Household notifications

## 8. Introduce a simple household-notification model
Add a small, configuration-driven household notification concept.

The design should support at least:
- identifying configured users by Telegram user/chat identity
- deciding which user is the initiator
- deciding which configured household recipient(s) should receive a notification

Keep this simple.
Do not build a large account-management subsystem.

Suggested concepts:
- household member mapping
- initiator identity
- notification recipient mapping
- feature flag for household notifications

---

## 9. Keep household mapping configuration-driven
Do not hardcode user ids or chat ids in the application logic.

Use existing configuration approach:
- `application.yml`
- `application-local.yml`
- `application-smoke.yml`
- explicit `@ConfigurationProperties`

At minimum, configuration should allow:
- feature enabled/disabled
- mapping for “me”
- mapping for “wife”
- recipient chat id(s)
- optional notification routing policy

---

## 10. Trigger notifications only after successful side effects
Household notifications must be sent only after the calendar action has actually succeeded.

This applies to:
- create event
- update/reschedule event
- cancel/delete event

Do not send household notifications when:
- execution guard rejected the action
- clarification is still pending
- confirmation was not granted
- the user cancelled before execution
- the Calendar API call failed
- the operation was a duplicate and did not re-execute

This requirement is central to the step.

---

## 11. Reuse the existing NotificationPort
Use the current notification abstraction.

Do not introduce a Telegram-specific shortcut in core logic.

The application/core should decide:
- whether a household notification should happen
- to whom it should be addressed
- what semantic message it should carry

The adapter should continue to own transport details.

---

## 12. Format household notifications clearly
Keep the message format simple and readable.

Example patterns:
- “Wife created event: Dentist, tomorrow at 14:00”
- “Wife rescheduled event: Dentist, Friday at 15:00”
- “Wife cancelled event: Dentist, tomorrow at 14:00”

You may localize or align wording to the current project language style, but keep it concise and explicit.

---

## 13. Avoid self-notifications and duplicates
Ensure:
- the initiator does not receive the same household notification as a self-notification
- duplicate execution does not generate duplicate household notifications
- missing recipient mapping does not crash the flow

If recipient configuration is missing, log clearly and skip the household notification safely.

---

# Integration requirements

## 14. Integrate guard before side effects
The flow should now conceptually be:

- message
- interpretation
- clarification / confirmation / resume
- calendar event draft
- execution guard
- calendar execution
- household notification (if configured and applicable)

This ordering is important.

Household notifications must happen only after:
- guard approval
- successful execution result

---

## 15. Preserve current continuation behavior
Guard logic and household notification logic must work with:
- fresh one-turn requests
- resumed clarification flows
- resumed confirmation flows

Do not break current continuation behavior.

---

## 16. Preserve architecture
The new logic must preserve:
- hexagonal architecture
- module boundaries
- provider isolation
- config split
- explicit `@ConfigurationProperties`
- NotificationPort abstraction
- deterministic application/core ownership of execution decisions

---

## Constraints

### Must preserve
- hexagonal architecture
- module boundaries
- provider isolation
- current clarification/confirmation flow
- current profile/config split
- deterministic application/core ownership of execution decisions

### Must not introduce
- Lambda runtime
- webhook support
- OpenClaw integration
- duplicated policy logic across adapters
- provider DTO leakage
- giant god-service
- hardcoded Telegram ids in use-case logic
- Telegram transport shortcuts in application/core

---

## Recommended implementation order

1. add guard model/interfaces/results
2. implement deterministic draft validation
3. add action-limiting policy
4. integrate guard into existing execution path
5. add household configuration model
6. add household notification decision service/policy
7. trigger household notification only after successful side effects
8. add focused tests

---

## Acceptance criteria

This step is acceptable if:

1. calendar execution no longer happens purely based on raw interpreted model output
2. there is a deterministic guard layer before `CalendarPort`
3. malformed or unsafe event drafts are rejected or downgraded safely
4. risky actions can require confirmation
5. if the wife successfully creates / reschedules / cancels an event, I receive a Telegram notification
6. if I perform the action myself, I do not receive a self-notification
7. if execution does not happen successfully, no household notification is sent
8. guard logic lives in application/core rather than adapters
9. household mapping is configuration-driven
10. tests cover allow/block/downgrade and household notification scenarios
11. the result prepares the system for safer future audio input

---

## Suggested test cases

Add focused tests for guard behavior:
- valid event allowed
- missing title rejected
- invalid time structure rejected
- oversized duration rejected or downgraded
- far-future event requires confirmation if policy says so
- recurring event requires confirmation
- guard decision is reflected in user-facing flow
- guard still works in resumed clarification/confirmation scenarios

Add focused tests for household notifications:
- create by wife -> notify me
- update by wife -> notify me
- cancel by wife -> notify me
- action by me -> no self-notification
- failed calendar action -> no notification
- missing recipient config -> no crash, clear handling
- duplicate execution -> no duplicate household notification

---

## Expected deliverables

After implementation, report back with:

1. which files/classes were added or significantly changed
2. how the execution guard was modeled
3. which deterministic checks were added
4. which actions now require confirmation or are rejected
5. how household user/recipient mapping was modeled
6. where the decision to send household notifications is made
7. how the guard integrates with current continuation flow
8. what remains intentionally simplified
9. what the recommended next step should be

---

## Final instruction

Do not try to solve safety only through prompting.
Do not bolt notifications directly into Telegram adapters.

This step is specifically about:
- deterministic execution validation
- explicit policy before side effects
- successful-action-based household notifications

Preserve the architecture first.
