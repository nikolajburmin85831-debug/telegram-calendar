# Assistant Core Architecture Specification v3
## EC2-first, Lambda-ready

## 1. Статус документа

**Статус:** Draft v3  
**Аудитория:** владелец проекта, будущая реализация, будущая архитектурная ревизия  
**Цель:** зафиксировать архитектуру personal assistant core, который на первом этапе работает как Telegram → Google Calendar ассистент на EC2, но спроектирован так, чтобы позже можно было без крупных переделок попробовать Lambda и при необходимости развивать проект дальше.

---

## 2. Назначение проекта

Проект создаётся как **личный ассистент**, который в первой версии:

- принимает сообщения пользователя из Telegram;
- интерпретирует естественный язык;
- при необходимости уточняет дату, время или другие параметры;
- создаёт события и напоминания в Google Calendar;
- возвращает пользователю подтверждение;
- поддерживает базовый пользовательский контекст и состояние диалога.

На текущем этапе проект **не строится как full agent platform** и **не зависит от OpenClaw**.  
Он строится как **самостоятельное assistant core**, которое в будущем можно расширять или интегрировать с внешним orchestration layer.

---

## 3. Архитектурная цель

Главная архитектурная цель:

**построить устойчивое ядро ассистента, независимое от конкретного транспорта, LLM-провайдера, runtime-среды и внешней платформы оркестрации.**

Это означает:

- Telegram не является центром архитектуры;
- Gemini не является внутренним языком системы;
- Google Calendar API не является внутренней моделью календаря;
- EC2 не определяет структуру кода;
- Lambda пока не реализуется, но её ограничения учитываются в архитектуре;
- OpenClaw не является частью текущего ядра.

---

## 4. Текущее архитектурное решение

Проект реализуется как:

**EC2-first, Lambda-ready modular monolith**  
на базе:

- Java
- Gradle
- Spring Boot
- hexagonal architecture

### Это означает

Сейчас реализуем:

- один runtime shell для EC2;
- inbound adapter для Telegram polling;
- outbound adapters для Gemini, Google Calendar, persistence и Telegram notifications;
- общий assistant core.

Сейчас **не реализуем**:

- `app-lambda`
- webhook ingress
- AWS Lambda handler
- API Gateway integration
- serverless bootstrap
- OpenClaw adapters

Но проект должен быть спроектирован так, чтобы их можно было добавить позже **без изменения domain/application/ports**.

---

## 5. Область применения v2

### 5.1. Что входит в текущую реализацию

- Telegram polling ingress
- нормализация входящих сообщений
- интерпретация intent через LLM
- внутренняя canonical intent model
- conversation state
- policy-based execution
- создание событий в Google Calendar
- отправка ответов в Telegram
- базовое хранение состояния
- idempotency boundary
- Gradle modular monolith
- runtime shell для EC2

### 5.2. Что сознательно не входит сейчас

- Telegram webhook
- Lambda runtime shell
- OpenClaw integration
- multi-channel ingress
- browser automation
- generalized plugin platform
- complex autonomous multi-step agent behavior
- полноценный production-grade automation engine

---

## 6. Нецели

Следующее **не является целью текущей версии**:

- построить систему вокруг Telegram DTO;
- построить систему вокруг Gemini response format;
- сделать Google Calendar внутренним языком предметной области;
- сделать polling основным architectural assumption;
- хранить correctness-critical state только в памяти процесса;
- начать с serverless-инфраструктуры;
- внедрить OpenClaw заранее “на всякий случай”;
- оптимизировать скорость первого прототипа ценой архитектурного долга.

---

## 7. Ключевые архитектурные принципы

1. **Центр архитектуры — assistant core, а не Telegram и не Spring Boot.**
2. **Все внешние интеграции проходят через порты и адаптеры.**
3. **Domain и Application не зависят от Telegram, Gemini, Google Calendar, EC2, Lambda или OpenClaw.**
4. **LLM возвращает canonical internal intent semantics, а не provider-specific actions.**
5. **Execution policy отделена от intent interpretation.**
6. **Conversation state моделируется явно.**
7. **Все correctness-critical данные должны быть абстрагированы через persistence ports.**
8. **Polling — это только ingress adapter, а не способ существования системы.**
9. **EC2 — это текущая runtime-оболочка, а не архитектурный центр.**
10. **Lambda compatibility достигается через границы, а не через преждевременную реализацию второй ветки.**

---

# 8. Архитектурный стиль

Проект строится как **hexagonal architecture / ports-and-adapters**.

Логическая направленность зависимостей:

- `Domain` зависит ни от кого;
- `Ports` зависят от `Domain`;
- `Application` зависит от `Domain` и `Ports`;
- `Adapters` зависят от `Ports` и внешних SDK;
- `App-EC2` собирает всё в работающий runtime.

---

# 9. Слойная модель

## 9.1. Domain Layer

### Назначение
Хранит внутренний смысл системы:
- сущности;
- value objects;
- состояния;
- политики;
- доменные ограничения.

### Что содержит
- `UserIdentity`
- `UserContext`
- `AssistantIntent`
- `IntentType`
- `IntentInterpretation`
- `CalendarEventDraft`
- `ClarificationRequest`
- `PendingConfirmation`
- `ConversationState`
- `ExecutionDecision`
- `ExecutionResult`
- `ConfidenceScore`
- `Timezone`
- `RecurrenceRule`

### Что не содержит
- Spring annotations
- Telegram DTO
- Gemini JSON
- Google SDK models
- HTTP client logic
- EC2 runtime assumptions

---

## 9.2. Ports Layer

### Назначение
Описывает контракты между assistant core и внешним миром.

### Inbound ports
- `HandleIncomingMessageUseCase`
- `ConfirmPendingActionUseCase`
- `ResumeConversationUseCase`

### Outbound ports
- `IntentInterpreterPort`
- `CalendarPort`
- `NotificationPort`
- `UserContextPort`
- `ConversationStatePort`
- `IdempotencyPort`
- `AuditPort`
- `SchedulerPort`

### Роль
Порты фиксируют архитектурную границу и делают возможной замену адаптеров без переписывания ядра.

---

## 9.3. Application Layer

### Назначение
Реализует use cases и orchestration прикладной логики.

### Что делает
- принимает нормализованное сообщение;
- загружает контекст пользователя;
- загружает состояние диалога;
- вызывает interpreter port;
- применяет policy;
- выбирает handler;
- инициирует создание календарного события;
- инициирует запрос уточнения;
- инициирует подтверждение действия;
- формирует user-facing результат.

### Пример смысловых элементов
- `HandleIncomingMessageService`
- `CreateCalendarEventHandler`
- `ClarificationHandler`
- `ConfirmationPolicyService`
- `IntentRoutingService`

### Что не делает
- не вызывает Telegram напрямую;
- не строит HTTP запросы;
- не работает с Gemini raw response;
- не зависит от Google SDK;
- не зависит от Spring Boot runtime.

---

## 9.4. Adapters Layer

### Назначение
Преобразует внешние API и transport formats во внутренние контракты системы.

### Текущие адаптеры
- `adapter-in-telegram-polling`
- `adapter-out-gemini`
- `adapter-out-google-calendar`
- `adapter-out-persistence`
- `adapter-out-notification-telegram`

### Будущие адаптеры, не реализуемые сейчас
- `adapter-in-telegram-webhook`
- потенциальный `adapter-in-openclaw`
- другие outbound adapters по мере роста системы

---

## 9.5. Infrastructure / Runtime Layer

### Назначение
Содержит runtime wiring и техническую сборку.

### Сейчас
Реализуется через `app-ec2`, который:
- поднимает Spring Boot;
- собирает модули;
- подключает polling;
- конфигурирует adapter implementations;
- управляет profiles и bootstrap.

### Позже
Может появиться отдельный `app-lambda`, но **не сейчас**.

---

# 10. Модульная структура проекта

Проект должен быть реализован как modular monolith с модулями:

```text
assistant/
├─ settings.gradle
├─ build.gradle
├─ assistant-domain/
├─ assistant-ports/
├─ assistant-application/
├─ adapter-in-telegram-polling/
├─ adapter-out-gemini/
├─ adapter-out-google-calendar/
├─ adapter-out-persistence/
├─ adapter-out-notification-telegram/
└─ app-ec2/
```

---

## 10.1. `assistant-domain`

### Роль
Чистое доменное ядро.

### Внутренние области
- `common`
- `user`
- `intent`
- `calendar`
- `conversation`
- `policy`
- `execution`

### Критерий
Любой класс, который можно описать без упоминания API, webhook, bot token, WebClient или provider SDK, скорее всего принадлежит сюда.

---

## 10.2. `assistant-ports`

### Роль
Архитектурные контракты.

### Внутренние области
- `ports.in`
- `ports.out`

### Критерий
Любая внешняя способность сначала должна быть определена как порт.

---

## 10.3. `assistant-application`

### Роль
Use cases и orchestration.

### Внутренние области
- `usecase`
- `service`
- `handler`
- `orchestration`
- `command`
- `result`

### Критерий
Здесь живёт логика вида:
- “что означает это сообщение?”
- “можно ли исполнять это автоматически?”
- “нужно ли уточнение?”
- “какой handler должен сработать?”

---

## 10.4. `adapter-in-telegram-polling`

### Роль
Приём Telegram updates через polling.

### Обязанности
- ходить в `getUpdates`
- получать update
- нормализовать в `IncomingUserMessage`
- вызывать inbound use case

### Важное ограничение
Не содержит business logic, calendar logic, conversation rules.

---

## 10.5. `adapter-out-gemini`

### Роль
Реализация `IntentInterpreterPort`.

### Обязанности
- формировать запрос к Gemini
- получать ответ
- переводить provider-specific output в internal canonical interpretation

### Важное ограничение
Gemini response shape не выходит за границы модуля.

---

## 10.6. `adapter-out-google-calendar`

### Роль
Реализация `CalendarPort`.

### Обязанности
- брать `CalendarEventDraft`
- превращать его в Google request
- вызывать Google Calendar API
- возвращать внутренний результат

### Внутренние зоны
- `client`
- `oauth`
- `mapper`
- `config`
- `service`

### Важное ограничение
Google Calendar SDK заканчивается внутри этого модуля.

---

## 10.7. `adapter-out-persistence`

### Роль
Реализация persistence-портов.

### На старте
Допустимы in-memory реализации:
- `UserContextPort`
- `ConversationStatePort`
- `IdempotencyPort`
- `AuditPort`

### Важное правило
Даже временные in-memory реализации должны быть **за портами**, а не внутри application layer.

---

## 10.8. `adapter-out-notification-telegram`

### Роль
Исходящие сообщения в Telegram.

### Обязанности
- брать внутренний ответ системы
- преобразовывать в outbound Telegram message
- отправлять пользователю сообщение

### Важное правило
Inbound и outbound Telegram concerns разделяются.

---

## 10.9. `app-ec2`

### Роль
Текущая runtime-оболочка проекта.

### Обязанности
- Spring Boot bootstrap
- profile management
- wiring модулей
- запуск polling
- конфигурация runtime

### Важное ограничение
Не содержит бизнес-логики.

---

# 11. Зависимости между модулями

Логическая схема:

```text
assistant-domain
    ↑
assistant-ports
    ↑
assistant-application

adapter-in-telegram-polling --------┐
adapter-out-gemini -----------------┤
adapter-out-google-calendar --------┤
adapter-out-persistence ------------┤
adapter-out-notification-telegram --┤
                                    ↓
                                 app-ec2
```

### Правила зависимостей
- `assistant-domain` ни от кого не зависит;
- `assistant-ports` зависит от `assistant-domain`;
- `assistant-application` зависит от `assistant-domain` и `assistant-ports`;
- адаптеры зависят от `assistant-ports` и внешних библиотек;
- `app-ec2` зависит от модулей и склеивает их.

---

# 12. Ключевые подсистемы

## 12.1. Message Intake Subsystem

### Назначение
Получение внешнего сообщения и превращение его в нормализованную внутреннюю модель.

### Сейчас
Telegram polling.

### Потом
Telegram webhook или другой канал.

### Правило
Ни один transport-specific объект не должен проникать глубже этой подсистемы.

---

## 12.2. Intent Interpretation Subsystem

### Назначение
Понимание намерения пользователя.

### Обязанности
- определить intent;
- извлечь сущности;
- выявить неоднозначности;
- вернуть confidence;
- указать, можно ли идти дальше.

### Правило
Возвращается internal semantic model, а не raw provider response.

---

## 12.3. Conversation Management Subsystem

### Назначение
Управление состоянием диалога.

### Типовые состояния
- `IDLE`
- `AWAITING_DATE`
- `AWAITING_TIME`
- `AWAITING_CONFIRMATION`
- `EXECUTING_ACTION`
- `COMPLETED`
- `FAILED`

### Почему это важно
Без этого любая multi-turn логика превращается в хаос внутри Telegram service.

---

## 12.4. Action Execution Subsystem

### Назначение
Выполнение действия, соответствующего intent.

### Примеры handlers
- `CreateCalendarEventHandler`
- `ClarificationHandler`
- `ConfirmationHandler`

### Правило
Каждое действие оформляется отдельным handler/use case, а не giant orchestrator.

---

## 12.5. Persistence and State Subsystem

### Назначение
Хранение всей correctness-critical state.

### Что сюда входит
- user context
- conversation state
- pending confirmations
- idempotency markers
- audit data

### Архитектурное правило
Нельзя полагаться на runtime memory как на источник истины.

---

## 12.6. Scheduling Abstraction Subsystem

### Назначение
Подготовка к будущим delayed/recurring сценариям.

### Сейчас
Может быть почти пустой.

### Но
`SchedulerPort` должен существовать уже сейчас, чтобы later scheduling не вшивался в core.

---

# 13. Канонические внутренние контракты

## 13.1. `IncomingUserMessage`
Нормализованное сообщение на входе в core.

Содержит:
- internal message id
- external message id
- user id
- channel type
- conversation id
- text
- received timestamp

---

## 13.2. `UserContext`
Пользовательский контекст.

Содержит:
- user id
- preferred timezone
- preferred language
- confirmation preference
- default event duration
- active conversation reference

---

## 13.3. `IntentInterpretation`
Внутреннее представление распознанного намерения.

Содержит:
- intent type
- entities
- confidence
- ambiguity markers
- missing fields
- safe-to-execute marker

---

## 13.4. `CalendarEventDraft`
Черновик календарного события до внешнего вызова.

Содержит:
- title
- description
- start
- end or duration
- timezone
- recurrence if needed
- location if needed
- source metadata

---

## 13.5. `ClarificationRequest`
Запрос уточнения.

Содержит:
- reason
- missing fields
- user-facing question
- pending action reference

---

## 13.6. `PendingConfirmation`
Ожидающее подтверждения действие.

Содержит:
- pending action id
- user id
- action summary
- confirmation prompt
- expiration policy
- payload reference

---

## 13.7. `ExecutionDecision`
Решение после interpretation + policy.

Возможные outcomes:
- execute now
- ask for clarification
- ask for confirmation
- reject
- defer

---

## 13.8. `ExecutionResult`
Результат выполнения.

Содержит:
- success/failure
- created resource reference
- user summary
- audit details
- next step hint if relevant

---

# 14. Основные runtime flows

## 14.1. Happy path
1. Telegram polling получает update.
2. Update нормализуется в `IncomingUserMessage`.
3. Application загружает `UserContext` и `ConversationState`.
4. Interpreter возвращает `IntentInterpretation`.
5. Policy разрешает выполнение.
6. Создаётся `CalendarEventDraft`.
7. `CalendarPort` создаёт событие.
8. `NotificationPort` отправляет подтверждение в Telegram.
9. State обновляется до `COMPLETED`.

---

## 14.2. Clarification path
1. Сообщение получено.
2. Intent распознан, но данных недостаточно.
3. Создаётся `ClarificationRequest`.
4. Пользователю задаётся уточняющий вопрос.
5. Conversation state меняется на соответствующее `AWAITING_*`.
6. Следующее сообщение трактуется как продолжение pending flow.

---

## 14.3. Confirmation path
1. Intent понятен.
2. Policy требует подтверждения.
3. Создаётся `PendingConfirmation`.
4. Пользователь получает confirmation prompt.
5. После подтверждения выполняется действие.
6. Pending state очищается.

---

## 14.4. Failure path
1. Ошибка интерпретации или интеграции.
2. Application классифицирует ошибку.
3. Пользователь получает либо recoverable prompt, либо failure summary.
4. Audit сохраняется.
5. State переводится в корректное состояние.

---

# 15. Deployment strategy

## 15.1. Текущий deployment mode
**Только EC2.**

### Это означает
- long-lived Spring Boot process;
- Telegram polling;
- традиционный bootstrap;
- удобная отладка;
- меньше operational novelty.

---

## 15.2. Lambda-ready design
Хотя Lambda сейчас не реализуется, архитектура должна учитывать следующее:

- ingress later можно заменить на webhook;
- runtime process memory не должен быть источником истины;
- polling не должен протекать в core;
- состояние должно быть externalizable;
- bootstrap не должен смешиваться с use case логикой.

---

## 15.3. Что именно резервируется под будущую Lambda
Пока не реализуем, но архитектурно допускаем:

- `adapter-in-telegram-webhook`
- `app-lambda`

Их добавление в будущем не должно требовать изменения:
- `assistant-domain`
- `assistant-ports`
- `assistant-application`

---

# 16. Что нельзя делать, если хотим сохранить Lambda-ready архитектуру

1. Передавать Telegram update в application layer.
2. Хранить pending confirmations только в singleton memory.
3. Делать polling scheduler частью business logic.
4. Пускать Gemini raw JSON в use cases.
5. Использовать Google SDK models в domain/application.
6. Смешивать inbound Telegram logic и outbound Telegram notifications.
7. Писать giant orchestrator, который и transport, и parsing, и policy, и execution одновременно.

---

# 17. Ошибки архитектуры, которые будут считаться регрессией

- Telegram polling service знает про intent routing, confirmation policy и calendar execution.
- Application layer зависит от Gemini response shape.
- Domain model знает про Google event resource.
- Runtime state является единственным источником pending flows.
- Нет отдельной idempotency boundary.
- Нет conversation state abstraction.
- EC2-specific assumptions встроены в core.
- Логика “потом будет Lambda” оправдывает плохие границы сейчас.

---

# 18. OpenClaw strategy

## Текущее решение
OpenClaw **не внедряется**.

## Причина
На текущем этапе проект решает узкую задачу:
- Telegram
- intent interpretation
- calendar action
- confirmation/clarification flow

OpenClaw здесь избыточен.

## Будущее
Если проект вырастет до:
- multi-channel assistant
- automation layer
- standing orders
- richer memory
- orchestration across tools

тогда OpenClaw может быть рассмотрен как:
- outer orchestration layer
- additional channel layer
- automation layer

Но не как замена assistant core.

---

# 19. Минимальный практический старт

## В `assistant-domain`
минимально реализовать:
- intent
- calendar draft
- conversation state
- execution decision
- user context

## В `assistant-ports`
минимально реализовать:
- `HandleIncomingMessageUseCase`
- `IntentInterpreterPort`
- `CalendarPort`
- `NotificationPort`
- `ConversationStatePort`
- `UserContextPort`
- `IdempotencyPort`

## В `assistant-application`
минимально реализовать:
- `HandleIncomingMessageService`
- `CreateCalendarEventHandler`
- `ClarificationHandler`
- `ConfirmationPolicyService`

## В адаптерах
реализовать:
- Telegram polling inbound
- Gemini outbound
- Google Calendar outbound
- In-memory persistence
- Telegram outbound notification

## В `app-ec2`
реализовать:
- Spring Boot bootstrap
- profiles
- wiring
- startup

---

# 20. Краткое итоговое решение

Проект реализуется как:

**assistant core в hexagonal architecture, modular monolith, EC2-first, Lambda-ready**

### Сейчас:
- только EC2 runtime
- только Telegram polling ingress
- только текущий набор адаптеров
- без Lambda ветки
- без OpenClaw

### Обязательно уже сейчас:
- canonical internal contracts
- ports for all external capabilities
- explicit conversation state
- explicit policy layer
- persistence abstractions
- idempotency boundary
- separation of runtime shell from core

### Позже можно добавить:
- webhook ingress
- lambda runtime shell
- richer persistence
- scheduling backend
- additional tools
- OpenClaw integration as outer layer

---

# 21. Continuity summary для нового чата

Если нужно быстро восстановить контекст в новом диалоге, можно вставить это:

> Я строю personal assistant core на Java/Gradle/Spring Boot как modular monolith с hexagonal architecture. Проект сейчас реализуется как EC2-first, Lambda-ready. Реализуем только EC2 runtime shell (`app-ec2`) и Telegram polling ingress. Core состоит из `assistant-domain`, `assistant-ports`, `assistant-application`. Адаптеры: `adapter-in-telegram-polling`, `adapter-out-gemini`, `adapter-out-google-calendar`, `adapter-out-persistence`, `adapter-out-notification-telegram`. Domain/Application/Ports не должны зависеть от Telegram, Gemini, Google Calendar, EC2, Lambda или OpenClaw. Lambda пока не реализуем, но архитектура должна позволить позже добавить `adapter-in-telegram-webhook` и `app-lambda` без изменения core. OpenClaw сейчас не внедряем.
