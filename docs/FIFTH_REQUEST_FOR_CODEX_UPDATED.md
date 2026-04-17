# FIFTH_REQUEST_FOR_CODEX.md

## Purpose

Use this request **after** the fourth iteration is complete and the project already has:

- real Telegram polling through `getUpdates`
- real Telegram outbound replies through `sendMessage`
- working conversational flows for success / clarification / confirmation / cancel
- improved continuation and enrichment behavior
- optional real Gemini path behind configuration
- optional real Google Calendar path behind configuration
- still in-memory conversation/idempotency/audit state
- still incomplete Google OAuth lifecycle

This fifth step should make the project significantly more robust as an **EC2-first real assistant runtime**.

The priorities for this step are intentionally ordered:

1. durable persistence for conversation state, idempotency, audit, and user context
2. fuller Google OAuth flow with refresh support
3. a secrets-based smoke profile for real Telegram + Gemini + Google Calendar testing

Do **not** broaden scope into Lambda, webhook, OpenClaw, or unrelated features.

---

## Source of truth

Use these files as architectural and implementation context:

- `docs/assistant_core_architecture_spec_v3.md`
- `docs/IMPLEMENTATION_SCOPE_V1_FOR_CODEX.md`
- `docs/SECOND_REQUEST_FOR_CODEX.md`
- `docs/THIRD_REQUEST_FOR_CODEX.md`
- `docs/FOURTH_REQUEST_FOR_CODEX.md`

Also inspect the current repository state before making changes.

---

## Main target

Implement the fifth iteration so the assistant becomes **state-safe across restarts**, **Google-auth-capable**, and **practical to smoke test with real secrets** on EC2-style runtime.

At the end of this step, the preferred outcome is:

- conversation state survives application restart
- idempotency survives restart
- audit information is persisted durably
- user context is persisted durably
- Google Calendar real mode can obtain and refresh tokens through a cleaner OAuth flow
- there is one clear smoke-test profile for real Telegram + Gemini + Google Calendar
- architecture remains intact and dev/test fallback modes remain available

---

## What to implement now

### 1. Replace in-memory correctness-critical state with durable persistence
Implement durable persistence adapters for the application state that must survive restart.

Priority persistence concerns:
- conversation state
- pending action state
- idempotency markers
- audit records
- user context

Requirements:
- preserve existing ports if possible
- keep in-memory implementations available for tests/local lightweight mode
- add durable implementations behind configuration
- do not push persistence technology details into application/domain layers

A simple relational database approach is acceptable for this step if it keeps the design clean.

---

### 2. Keep persistence boundaries clean
Refine persistence-related abstractions only if necessary.

Expected behavior:
- restarting the app must not lose pending clarification/confirmation state
- restarting the app must not lose idempotency markers needed to avoid duplicate execution
- user context should survive restart
- audit records should be queryable or at least durably stored

Do not let persistence concerns leak into Telegram adapters or bootstrap code.

---

### 3. Implement a more complete Google OAuth flow
Improve Google Calendar real mode so it is no longer just access-token-oriented.

Target capabilities:
- load configured OAuth credentials cleanly
- obtain access token from refresh token where applicable
- handle token refresh when needed
- keep Google auth lifecycle inside the calendar adapter/auth support area
- preserve `CalendarPort` boundary

If full user-consent bootstrap is too large, at minimum implement:
- refresh-token-based access-token acquisition
- refresh lifecycle support
- clean configuration model for credentials

Do not leak Google-specific auth structures outside the adapter.

---

### 4. Introduce a secrets-based smoke profile
Create a dedicated runtime profile for real end-to-end smoke testing.

Expected goals:
- real Telegram polling
- real Telegram replies
- real Gemini path
- real Google Calendar path
- durable persistence
- configuration driven by environment variables or secret-backed config

This profile should be clearly separated from:
- stub/local dev mode
- test mode

Keep it practical and explicit.

---

### 5. Preserve the current config split
Preserve and use the existing split configuration approach:

- `application.yml`
- `application-local.yml`
- `application-smoke.yml`

Requirements:
- do not collapse these into a single config file
- keep `application.yml` as the shared base
- keep `application-local.yml` for stub/in-memory/local behavior
- keep `application-smoke.yml` for real Telegram + Gemini + Google Calendar + durable persistence behavior
- wire the fifth-step behavior through these profiles cleanly

The purpose of this split is to keep local development safe and simple while making smoke testing explicit and controlled.

---

### 6. Bind configuration through explicit @ConfigurationProperties
Use explicit `@ConfigurationProperties` classes aligned with the `assistant.*` configuration sections.

Requirements:
- prefer configuration classes over scattering raw `@Value` usage across the codebase
- keep the config model aligned with the existing YAML structure
- preserve clean boundaries between runtime wiring and business logic
- make real vs stub mode selection configuration-driven and easy to understand

This is especially important now that the project has multiple modes:
- local
- smoke
- in-memory
- durable persistence
- stub adapters
- real adapters

---

### 7. Preserve fallback and test modes
Do not remove the existing stub/in-memory modes.

Expected:
- tests should still run without real external services
- local lightweight development should still be possible
- real smoke profile should be opt-in through configuration

The architecture should support both real and non-real modes cleanly.

---

### 8. Add focused tests for durability-sensitive behavior
Add tests that prove the new persistence and auth boundaries behave correctly.

Good candidates:
- persistence adapter tests
- state reload tests
- idempotency persistence tests
- configuration selection tests
- Google OAuth helper/auth support tests
- application-level restart-safe behavior tests where feasible

Prefer high-value tests over broad noisy coverage.

---

## Constraints

### Must preserve
- hexagonal architecture
- module boundaries
- provider isolation
- EC2-first, Lambda-ready design
- conversation logic in application/core
- adapters responsible for provider and persistence specifics
- ability to run tests without real secrets

### Must not introduce
- Lambda runtime
- webhook support
- OpenClaw integration
- Telegram provider DTO leakage into application/domain
- Google provider/auth leakage outside adapter boundary
- giant infrastructure rewrite
- business logic in Spring Boot bootstrap
- removal of existing stub/test paths
- collapsing profile-specific config into one file
- broad raw `@Value`-based config sprawl

---

## Recommended implementation order

Follow this order unless there is a strong reason not to:

1. durable persistence adapters
2. configuration selection between in-memory and durable modes
3. explicit `@ConfigurationProperties` alignment with the YAML sections
4. Google OAuth refresh-token support
5. secrets-based smoke profile
6. focused tests and cleanup

This order is important.

The most valuable improvement at this point is surviving restart safely.

---

## Acceptance criteria

This fifth step is acceptable if:

1. pending conversation state survives application restart in durable mode
2. idempotency survives restart in durable mode
3. user context survives restart in durable mode
4. audit data is durably stored
5. Google Calendar real mode supports a cleaner OAuth/refresh lifecycle than before
6. there is a dedicated real smoke profile using secrets/config
7. the split between `application.yml`, `application-local.yml`, and `application-smoke.yml` is preserved and used cleanly
8. configuration is bound through explicit `@ConfigurationProperties` classes aligned to the YAML structure
9. existing architecture and module boundaries remain intact
10. tests and stub/local modes still work

---

## Expected deliverables

After implementation, report back with:

1. which files/classes were added or significantly changed
2. which persistence technology and structure were chosen
3. which state now survives restart
4. what Google OAuth capabilities are now implemented
5. how the smoke profile is configured
6. how the config split and `@ConfigurationProperties` model were implemented
7. what is still stubbed or simplified
8. any architectural tradeoffs made
9. what the recommended sixth step should be

---

## Final instruction

Do not turn this into a broad infrastructure expansion.

This step is specifically about:
- durable persistence
- preserving the config split
- explicit `@ConfigurationProperties` binding
- Google OAuth lifecycle improvement
- secrets-based smoke profile

Preserve the architecture first.
