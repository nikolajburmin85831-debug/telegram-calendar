# IMPLEMENTATION_SCOPE_V1_FOR_CODEX

## Purpose

Use this file together with `assistant_core_architecture_spec_v2.md`.

This document defines the **first implementation scope** for the project so the initial code generation stays narrow, realistic, and aligned with the architecture.

The project is a **personal assistant core** built as a **Gradle modular monolith** on **Java + Spring Boot**, with **EC2-first, Lambda-ready** architecture.

At this stage, implement only the **first working skeleton** of the project.

---

## Primary objective

Create the initial project structure and code skeleton for a Telegram-to-Google-Calendar assistant.

The system should:

- accept incoming Telegram messages through polling
- normalize them into an internal message model
- interpret intent through an LLM adapter
- route the intent through application use cases
- create Google Calendar events through an outbound adapter
- send a reply back to Telegram
- keep conversation state and idempotency behind persistence ports

Do **not** implement Lambda or webhook support now.

---

## Source of truth

Architecture source of truth:
- `assistant_core_architecture_spec_v2.md`

This implementation scope must follow that architecture.

If something is unclear, prefer:
1. preserving architectural boundaries
2. keeping the first version small
3. using stubs instead of overbuilding

---

## Required modules

Create these Gradle modules:

```text
assistant-domain
assistant-ports
assistant-application
adapter-in-telegram-polling
adapter-out-gemini
adapter-out-google-calendar
adapter-out-persistence
adapter-out-notification-telegram
app-ec2
```

Do **not** create:
- `adapter-in-telegram-webhook`
- `app-lambda`
- any OpenClaw-related module

---

## Scope for v1

### Must implement

#### 1. Project structure
- Gradle multi-module setup
- root `settings.gradle`
- root `build.gradle`
- module build files as needed
- package structure aligned with the architecture

#### 2. Core domain skeleton
Create minimal domain objects needed for the first flow, including concepts such as:
- user context
- incoming user message
- intent type
- intent interpretation
- calendar event draft
- clarification request
- pending confirmation
- conversation state
- execution decision
- execution result

Keep domain models clean and provider-agnostic.

#### 3. Port interfaces
Create inbound and outbound ports for the first flow.

Minimum required outbound ports:
- `IntentInterpreterPort`
- `CalendarPort`
- `NotificationPort`
- `UserContextPort`
- `ConversationStatePort`
- `IdempotencyPort`

Minimum required inbound use case:
- `HandleIncomingMessageUseCase`

Optional if useful in the initial design:
- confirmation-related inbound use case
- resume conversation inbound use case

#### 4. Application layer skeleton
Create the first application services and handlers needed to process one inbound message.

Minimum expected pieces:
- incoming message handling service
- intent routing/orchestration service
- calendar event creation handler
- clarification handler
- confirmation policy service or equivalent policy component

The application layer should orchestrate the flow, but it is acceptable for some first-iteration handlers to be simple.

#### 5. Telegram polling inbound adapter
Implement:
- Telegram polling client/service
- update model or DTOs for Telegram polling
- mapping from Telegram update to internal incoming message
- invocation of the inbound use case

This should be enough to receive a message from Telegram and hand it to the application layer.

#### 6. Gemini outbound adapter
Implement a first version of the LLM adapter that:
- accepts internal interpretation input
- returns internal intent interpretation output

For v1, a stub or simplified implementation is acceptable if needed.
If a real implementation is added, keep provider-specific DTOs inside the adapter.

#### 7. Google Calendar outbound adapter
Implement a first version of the calendar adapter.

For v1:
- it may be a stub
- or it may contain a minimal real implementation scaffold

The important part is that the adapter consumes internal `CalendarEventDraft` and hides provider details.

#### 8. Persistence adapter
Implement in-memory versions for:
- user context storage
- conversation state storage
- idempotency storage

These are temporary implementations, but they must still respect the port boundaries.

#### 9. Telegram notification outbound adapter
Implement a first version of outbound user reply sending for Telegram.

This may be stubbed at first if needed, but the module and contract boundary must exist.

#### 10. EC2 runtime shell
Create the Spring Boot runtime shell in `app-ec2`:
- bootstrap class
- bean wiring
- config structure
- application profiles if useful
- module assembly

---

## Explicit non-goals for v1

Do **not** implement the following now:

- Lambda runtime shell
- Telegram webhook ingestion
- API Gateway integration
- AWS-specific infrastructure
- OpenClaw integration
- database-backed persistence
- full recurring scheduling engine
- full automation engine
- browser tools
- multi-channel support
- complex memory system
- polished production-grade observability
- fully finished Google OAuth flow unless needed for minimal scaffolding
- broad command coverage beyond the first main message flow

---

## First supported user flow

The first target flow is:

1. Telegram message arrives
2. polling adapter receives update
3. message is normalized to internal model
4. application layer loads state/context
5. interpreter returns intent interpretation
6. if enough data exists, application creates calendar event draft
7. calendar port is called
8. notification port sends confirmation
9. state is updated

Also support the shape for a clarification path:
- if required data is missing or ambiguous, produce a clarification request instead of direct execution

The clarification path may remain simple in v1, but the model and application boundary must exist.

---

## Coding constraints

### Keep these rules

1. Do not pass Telegram DTOs into domain or application layers.
2. Do not expose Gemini response JSON outside the Gemini adapter.
3. Do not expose Google Calendar SDK types outside the Google Calendar adapter.
4. Do not put business logic into the Telegram polling adapter.
5. Do not put business logic into Spring Boot bootstrap classes.
6. Do not store correctness-critical state directly in singleton runtime fields if that state belongs behind a persistence port.
7. Prefer small focused classes over one giant orchestrator.

### Architectural priority order

When in doubt, prefer:
1. clean boundaries
2. small working slice
3. stubs behind ports
4. later expansion support

---

## Suggested implementation depth

For the first iteration, prioritize **structure over completeness**.

That means:
- better to have correct modules, interfaces, and skeleton handlers
- than to fully implement external integrations while collapsing architecture

It is acceptable that some adapters are initially:
- stubbed
- partially implemented
- TODO-marked for real provider integration

as long as:
- the boundaries are correct
- the project compiles
- the main flow is visible in code

---

## Deliverables expected from Codex

### Minimum deliverables
- all required modules created
- Gradle configuration working
- package layout created
- core domain models created
- required ports created
- application use case skeleton created
- Telegram polling adapter created
- in-memory persistence adapter created
- notification adapter created
- EC2 app shell created

### Strongly preferred
- code compiles
- basic dependency flow is correct
- clear naming and package organization
- TODO markers for unfinished provider specifics
- no architecture-violating shortcuts

---

## Acceptance criteria for v1

The first implementation is acceptable if:

1. the repository has the expected modules
2. dependencies follow the intended architecture
3. the assistant core is separated from adapters and runtime
4. the main message-handling flow exists in code
5. persistence concerns are behind ports
6. Telegram polling is isolated in its adapter
7. the code is a valid foundation for later webhook/Lambda work
8. no OpenClaw-specific coupling is introduced

---

## What to optimize for

Optimize for:
- architectural correctness
- readability
- future extension
- compileability
- small working slice

Do not optimize for:
- feature completeness
- production hardening
- advanced infrastructure
- maximum abstraction count beyond what is useful now

---

## Final instruction

Implement the smallest correct version of the architecture.

If there is a tradeoff between:
- “more features now”
and
- “clean architecture that supports later growth”

choose the clean architecture.
