# Finance AI Service

AI-powered financial intelligence service built with Java, Spring Boot, Spring AI, and OpenAI.

The project is being developed as a progressive, production-oriented Finance AI platform. The implementation started with LLM-powered chat and conversation history, and has since grown to include financial calculator tools, RAG-based document grounding, LLM-based agent routing, document management, MCP server exposure, and a hardened cross-cutting layer of guardrails, resilience, async ingestion, and multi-agent routing. Auth and full productionization remain ahead.

---

## Project Overview

**Agentic AI Service** provides a conversational AI layer for a financial application.

The long-term goal is to allow a user to ask natural-language questions such as:

- What is my current account balance?
- How much did I spend on food last month?
- Show my largest transactions this year.
- Why did my expenses increase this month?
- How much should I keep as an emergency fund?
- Summarize my recent spending.

The AI service will progressively gain the ability to:

1. Understand natural-language financial questions.
2. Maintain conversation history.
3. Call financial tools.
4. Communicate with external tools/services through MCP.
5. Retrieve relevant financial knowledge using RAG.
6. Orchestrate multi-step Agentic AI workflows.
7. Integrate with production finance services.

---

## Current Architecture

```text
                              ┌─────────────────────┐
                              │       Client         │
                              └──────────┬──────────┘
                                         │ REST
                                         ▼
                              ┌─────────────────────┐
                              │   Chat Controller    │
                              └──────────┬──────────┘
                                         ▼
                              ┌─────────────────────┐
                              │  PromptGuardService   │  ← input screening
                              └──────────┬──────────┘
                                         ▼
                              ┌─────────────────────┐
                              │  IntentClassifierSvc  │  ← intent + query enrichment
                              └──────────┬──────────┘
                                         ▼
                    ┌───────────────  AgentRegistry  ───────────────┐
                    ▼                    ▼                    ▼                    ▼
          GeneralEducationAgent   SmallTalkAgent      DocumentQaAgent   AccountSpecificActionAgent
                    │                    │                    │                (stub — pending auth)
                    └────────────────────┴──────────┬─────────┘
                                                     ▼
                                      ┌─────────────────────┐
                                      │ ResilientLlmGateway   │  ← Resilience4j timeout + circuit breaker
                                      └──────────┬──────────┘
                                                 ▼
                                      ┌─────────────────────┐
                                      │  OutputGuardService   │  ← PII / account-number redaction
                                      └──────────┬──────────┘
                                                 ▼
                                      ┌─────────────────────┐
                                      │ ConversationAuditSvc  │  ← content encrypted at rest (AES-GCM)
                                      └──────────┬──────────┘
                                                 ▼
                                             PostgreSQL

  Document upload (async, via Kafka):

  Client → DocumentController → PendingUploadStorage + PENDING row → Kafka (document-uploaded)
                                                                            │
                                                          DocumentIngestionConsumer (manual ack)
                                                                            │
                                                          DocumentIngestionService.processIngestion
                                                                            │
                                                    PDF parse → chunk → embed → pgvector (status → READY/FAILED)
                                                                            │
                                                   on repeated failure → document-uploaded.DLT (14-day retention)
```

RAG retrieval (`DocumentQaAgent` → `RagChatService`) performs its own `vectorStore.similaritySearch(...)` rather than using Spring AI's `QuestionAnswerAdvisor` directly, so each retrieved chunk can be screened by `PromptGuardService.screenRetrievedContent(...)` before being assembled into the prompt — uploaded documents are treated as untrusted content, the same as user input.

The architecture will continue to evolve toward:

```text
Client
  │
  ▼
Finance AI Service
  │
  ▼
AI Orchestrator / Agent Registry
  │
  ├──────────────► LLM
  │
  ├──────────────► Conversation Memory (encrypted)
  │
  ├──────────────► Financial Tools
  │
  ├──────────────► MCP
  │
  └──────────────► RAG / Vector Search
                         │
                         ▼
                Finance Domain Services
```

---

## Technology Stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.x (Spring Framework 7) |
| AI Framework | Spring AI |
| LLM | OpenAI |
| API | REST |
| API Docs | springdoc-openapi (Swagger UI) |
| Persistence | PostgreSQL + pgvector |
| ORM | Spring Data JPA / Hibernate |
| Conversation Memory | Spring AI Chat Memory (JDBC-backed) + encrypted audit trail |
| Async Messaging | Apache Kafka (KRaft, `spring-boot-starter-kafka`) |
| Resilience | Resilience4j (timeout, circuit breaker, rate limiter) |
| Build | Maven |
| Boilerplate Reduction | Lombok |
| Observability | Spring Boot Actuator, Micrometer/Prometheus |
| Future Cache | Redis |
| Future Integration | MCP client (server-side MCP already exposed) |
| Future Deployment | Docker / Kubernetes / AWS |

---

## Project Coordinates

```text
Group ID     : com.ai.service
Artifact ID  : finance-ai-service
Version      : 0.0.1-SNAPSHOT
Java         : 21
Base Package : com.finance.ai
```

> The actual source tree uses `com.finance.ai` as its base package (not `com.ai.service`). Package names should remain consistent throughout the source tree going forward.

---

## Project Structure

```text
finance-ai-service
│
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── finance
│   │   │           └── ai
│   │   │               │
│   │   │               ├── FinanceAiServiceApplication.java
│   │   │               │
│   │   │               ├── chat
│   │   │               │   ├── controller     (ChatController, ConversationController)
│   │   │               │   ├── dto            (ChatRequest, ChatResponse, CreateConversationRequest, ConversationResponse, MessageResponse)
│   │   │               │   └── service        (ChatService — dispatches through AgentRegistry)
│   │   │               │
│   │   │               ├── agent
│   │   │               │   ├── FinanceAgent            (interface: supportedIntent, respond)
│   │   │               │   ├── AgentRegistry            (intent → agent dispatch)
│   │   │               │   ├── IntentClassifierService  (intent classification + query enrichment)
│   │   │               │   ├── model           (QueryIntent, QueryIntentResult)
│   │   │               │   └── impl            (GeneralEducationAgent, SmallTalkAgent,
│   │   │               │                         DocumentQaAgent, AccountSpecificActionAgent [stub])
│   │   │               │
│   │   │               ├── guardrail
│   │   │               │   ├── PromptGuardService   (input screening + RAG-chunk injection screening)
│   │   │               │   └── OutputGuardService   (PII / account-number redaction on LLM output)
│   │   │               │
│   │   │               ├── ratelimit
│   │   │               │   └── ChatRateLimitInterceptor  (per-client, per-endpoint rate limiting)
│   │   │               │
│   │   │               ├── llm
│   │   │               │   ├── config         (LlmConfig — ChatClient bean, tools, advisors)
│   │   │               │   └── service        (LlmService, ResilientLlmGateway — timeout + circuit breaker)
│   │   │               │
│   │   │               ├── memory
│   │   │               │   ├── crypto         (EncryptedStringConverter — AES-GCM audit encryption)
│   │   │               │   ├── model          (Conversation, ConversationMessageAudit)
│   │   │               │   ├── repository     (ConversationRepository, ConversationMessageAuditRepository)
│   │   │               │   └── service        (ConversationAuditService, MemoryService)
│   │   │               │
│   │   │               ├── tools
│   │   │               │   └── FinanceCalculatorTools   (@Tool: EMI, compound interest, SIP)
│   │   │               │
│   │   │               ├── mcp
│   │   │               │   └── config         (McpToolConfig — exposes tools via MCP server)
│   │   │               │
│   │   │               ├── rag
│   │   │               │   ├── controller     (DocumentController — async upload + status endpoint)
│   │   │               │   ├── dto            (IngestResponse, DocumentSummary — now status-aware)
│   │   │               │   ├── model          (UploadedDocument, DocumentStatus)
│   │   │               │   ├── repository     (DocumentRepository)
│   │   │               │   └── service        (DocumentIngestionService, DocumentIngestionEventProducer,
│   │   │               │                         DocumentIngestionConsumer, PendingUploadStorage,
│   │   │               │                         PdfFileValidator, RagChatService — chunk-screening)
│   │   │               │
│   │   │               ├── events
│   │   │               │   └── DocumentUploadedEvent   (Kafka event payload)
│   │   │               │
│   │   │               ├── exception          (LlmUnavailableException, ConversationNotFoundException,
│   │   │               │                        DocumentNotFoundException, InvalidFileException,
│   │   │               │                        PromptGuardException, EventPublishException,
│   │   │               │                        GlobalExceptionHandler)
│   │   │               │
│   │   │               ├── model              (MessageRole)
│   │   │               │
│   │   │               └── config             (KafkaProducerConfig, KafkaErrorHandlingConfig,
│   │   │                                         RateLimiterConfigBeans)
│   │   │
│   │   └── resources
│   │       ├── application.yaml
│   │       └── application-local.yaml   (host-run app against dockerized Kafka)
│   │
│   └── test
│
├── pom.xml
├── Dockerfile               (multi-stage build, non-root user, --chown fix applied)
├── docker-compose.yml       (Kafka KRaft + kafka-init + kafka-ui + app; local Postgres via host.docker.internal)
├── mvnw
├── mvnw.cmd
├── .gitignore
└── README.md
```

> Note: earlier drafts of this structure (and some AI-autocomplete suggestions during development) included additional subpackages — `tools/account`, `tools/transaction`, `tools/analytics`, `mcp/client`, `mcp/service`, `rag/ingestion`, `rag/embedding`, `rag/retrieval`, `agent/config`, `agent/state`, `agent/workflow` — that were never implemented with real logic and were removed. The structure above reflects what is actually built and wired in.

---

# Development Roadmap

The project is intentionally being developed in phases.

## Phase 1 — Basic Chat + LLM

**Goal:** Establish a working conversational API.

```text
Client
  ↓
ChatController
  ↓
ChatService
  ↓
LlmService
  ↓
ChatClient
  ↓
OpenAI
```

## Phase 2 — LLM & Prompt Engineering

System prompts, temperature/model configuration, prompt templates, response handling, error handling, LLM abstraction.

## Phase 3 — Conversation Memory

Conversation history is persisted per conversation ID, and message content is now encrypted at rest (see [Security, Guardrails & Resilience](#security-guardrails--resilience) below).

## Phase 4 — Financial Tools

Generic financial calculator tools (EMI, compound interest, SIP) are implemented and exposed both to the LLM directly and via MCP. Real domain tools (account balance, transactions, analytics) remain ahead — `AccountSpecificActionAgent` is currently a deliberate stub pending authentication.

## Phase 5 — MCP

The calculator tools are exposed as an MCP server (`POST /mcp`). An MCP *client* (consuming external MCP servers) is not yet built.

## Phase 6 — RAG

Implemented, with an added guardrail layer: retrieval is performed manually (not via the built-in advisor) so each retrieved chunk can be screened for prompt-injection before being added to context. See `RagChatService`.

## Phase 7 — Agentic AI

Implemented as a router + specialist agent architecture:

```text
                  ┌───────────────┐
                  │     User      │
                  └───────┬───────┘
                          ▼
                ┌───────────────────┐
                │  IntentClassifier  │  (intent + query enrichment)
                └─────────┬─────────┘
                          ▼
                ┌───────────────────┐
                │   AgentRegistry    │
                └─────────┬─────────┘
          ┌───────────────┼────────────────┬──────────────────┐
          ▼               ▼                ▼                  ▼
  GeneralEducation    SmallTalk      DocumentQa (RAG)   AccountSpecificAction
      Agent             Agent            Agent              (stub)
          │               │                │
          └───────────────┴────────────────┘
                          ▼
                ResilientLlmGateway → OpenAI
```

`ADVICE_REQUEST` and `OUT_OF_SCOPE` intents deliberately bypass the LLM entirely and return a fixed, deterministic response — treated as safety-critical enough that a scripted answer is preferable to a generated one.

---

# Security, Guardrails & Resilience

This layer was built across the whole request/response and ingestion path, independent of the phase roadmap above, since it cuts across chat, RAG, and document ingestion equally.

### Input & request validation
- `@Valid` enforced on all chat endpoints; `GlobalExceptionHandler` returns clean 4xx responses instead of stack traces.
- `PdfFileValidator` checks real PDF magic bytes (not just filename/content-type) and caps upload size at 20MB before a file is ever accepted.

### Prompt-injection defense
- `PromptGuardService.screenUserInput(...)` — pattern-based screening of user messages for instruction-override attempts, applied before any LLM call.
- `PromptGuardService.screenRetrievedContent(...)` — the same screening applied to every RAG-retrieved chunk individually. Uploaded PDFs are treated as untrusted content; a flagged chunk is excluded from context and logged with its source `document_id`, rather than failing the whole request.

### Output guardrail
- `OutputGuardService` screens LLM output for account/card-number-shaped and SSN-like patterns and redacts them before the reply reaches the client.

### Resilience
- `ResilientLlmGateway` wraps all LLM/RAG calls with Resilience4j `@TimeLimiter` + `@CircuitBreaker` (separate instances for `llmService` and `ragChatService`), backed by a dedicated bounded executor. Extracted into its own bean specifically to avoid Spring AOP's self-invocation limitation.
- `ChatRateLimitInterceptor` applies per-client (IP-keyed, pending auth) rate limits to `/api/chat/**`, with a stricter cap on `/agent`. Built from Java `RateLimiterConfig` beans rather than YAML-driven config, to avoid a dependency on named-configuration lookup at runtime.

### Async document ingestion (Kafka)
- Upload is fire-and-confirm, not fire-and-forget: `DocumentIngestionEventProducer` blocks for broker acknowledgement (bounded by a timeout) and throws `EventPublishException` on failure, so a `202` response is only ever returned once the event has genuinely reached the broker.
- `DocumentIngestionConsumer` uses manual offset acknowledgement, committed only after ingestion (including the DB status write) durably succeeds — a mid-processing crash results in safe redelivery, not silent message loss.
- Failures retry with exponential backoff (`ExponentialBackOffWithMaxRetries`) and route to `document-uploaded.DLT` after repeated failure, rather than blocking the partition indefinitely or retrying forever.
- Kafka topics are created explicitly (not auto-created) with topic-specific retention: 3 days for `document-uploaded`, 14 days for its DLT — long enough for a human to notice and investigate a failed ingestion.
- Document status (`PENDING` → `PROCESSING` → `READY`/`FAILED`) is queryable via `GET /api/documents/{id}/status` while ingestion runs in the background.

### Data protection
- `ConversationMessageAudit.content` is encrypted at rest with AES-GCM (`EncryptedStringConverter`), keyed by an externalized `AUDIT_ENCRYPTION_KEY` — a database leak does not expose plaintext conversation history.
- Raw message/reply content was removed from application logs (e.g. `IntentClassifierService` now logs intent and content *length*, not content itself) — logs typically ship to less-secured aggregation systems than the primary database.

### Known, currently unresolved gap
- **JWT audience validation.** `spring.security.oauth2.resourceserver.jwt.issuer-uri` validates issuer/expiry only; without an explicit `JwtDecoder` bean adding an audience check against `app.security.google-client-id`, any valid Google-issued token — not only ones minted for this app — currently authenticates successfully. This is the most significant open security item.

---

# Productionization Roadmap

### Persistence
PostgreSQL, database migrations (Flyway/Liquibase — not yet adopted; `ddl-auto: update` and Spring AI's `initialize-schema: always`/`true` remain auto-DDL for now), transaction management, indexing, query optimization.

### Caching
Redis — not yet started.

### Messaging
Apache Kafka — **implemented** for document ingestion (see above). Broader event-driven processing (guardrail-violation events, agent tool-invocation audit trail) discussed but not yet built.

### Security
- Authentication/Authorization/JWT — partially configured (OAuth2 resource server against Google), audience validation still missing (see above).
- User-level data isolation — not yet implemented; `userId` is currently client-supplied and unverified. Document retrieval and conversation access are not yet tenant-scoped.
- API validation — implemented (`@Valid`, file validation, prompt/output guardrails).
- Secret management — environment-variable based for now (`.env`, not committed); a proper secrets manager (AWS Secrets Manager, Vault) remains a future step, particularly for `AUDIT_ENCRYPTION_KEY` rotation.

### Observability
Actuator + Micrometer/Prometheus configured. Structured logging, distributed tracing, Grafana dashboards, and guardrail-trigger-rate metrics remain future work.

### Deployment
```text
Docker (implemented — multi-stage Dockerfile, docker-compose for local Kafka + app)
   ↓
Kubernetes (not started)
   ↓
AWS (not started)
```

---

# Configuration

The application uses environment variables for secrets. Required variables:

```text
OPENAI_API_KEY
POSTGRES_PASSWORD
GOOGLE_CLIENT_ID
AUDIT_ENCRYPTION_KEY       # generate with: openssl rand -base64 32
```

Do not commit secrets to source control. `.env` should be in `.gitignore`.

---

# Running Locally

## Option A — Fully containerized (Kafka + app)

```bash
docker compose up -d --build
docker compose ps   # confirm kafka, kafka-ui, app all show (healthy)
```
App reachable at `http://localhost:9090` (or whatever host port is mapped in `docker-compose.yml`).

## Option B — Kafka in Docker, app run locally (recommended for active debugging)

```bash
docker compose up -d kafka kafka-init kafka-ui
```

Then run the app with the `local` profile active (overrides Kafka/datasource hosts to `localhost`):

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

(or set `SPRING_PROFILES_ACTIVE=local` plus the required secrets as environment variables in your IDE's run configuration)

---

# API Reference

All endpoints are served under `http://localhost:9090` (or the mapped Docker port).

## Chat

| Method | Path | Description |
|---|---|---|
| POST | `/api/chat` | Chat, routed through intent classification and the agent registry |
| POST | `/api/chat/rag` | Chat with retrieval always applied against uploaded documents |
| POST | `/api/chat/agent` | Chat with full intent-based agent routing |

Request body (all three):
```json
{
  "message": "Explain what an emergency fund is in simple terms.",
  "conversationId": null,
  "userId": null
}
```

Response body:
```json
{
  "reply": "...",
  "conversationId": "b7e2...",
  "classifiedAsRag": true
}
```

## Conversations

| Method | Path | Description |
|---|---|---|
| POST | `/api/conversations` | Explicitly create a new, empty conversation |
| GET | `/api/conversations/{id}/messages` | Fetch full message history for a conversation, chronological |

## Documents (RAG, async via Kafka)

| Method | Path | Description |
|---|---|---|
| POST | `/api/documents/upload` | Upload a PDF (multipart, field `file`) — returns `202` once queued, not once processed |
| GET | `/api/documents/{id}/status` | Poll ingestion status: `PENDING` → `PROCESSING` → `READY`/`FAILED` |
| GET | `/api/documents` | List all uploaded documents with status, chunk counts, upload timestamps |
| DELETE | `/api/documents/{id}` | Delete a document and its associated vector store chunks |

## MCP

Finance calculator tools (`calculateEmi`, `calculateCompoundInterest`, `calculateSip`) are exposed as an MCP server at `POST http://localhost:9090/mcp`.

---

# Health Check

```http
GET http://localhost:9090/actuator/health
```

---

# Current Implementation Status

```text
[✓] Project foundation
[✓] Spring Boot application (Spring Boot 4.1.x / Spring Framework 7)
[✓] LLM configuration (ChatClient, system prompt, chat memory advisor)
[✓] OpenAI integration (chat + embeddings)
[✓] Chat API — tested
[✓] Conversation lifecycle (create, resolve, 404 on unknown id) — tested
[✓] Persistent conversation history — tested, content encrypted at rest (AES-GCM)
[✓] Financial calculator tools (EMI, compound interest, SIP) — tested
[✓] RAG ingestion (PDF → chunks → pgvector), now async via Kafka — tested
[✓] Vector retrieval with per-chunk prompt-injection screening — tested
[✓] Intent classification + query enrichment (standalone-query rewriting) — tested
[✓] Multi-agent routing (AgentRegistry + per-intent specialist agents) — tested
[✓] Document management (list, status polling, delete with vector cleanup) — tested
[✓] MCP server exposure (calculator tools via MCP protocol) — tested
[✓] Input validation + centralized exception handling
[✓] Prompt-injection guardrail (user input + RAG-retrieved content)
[✓] Output guardrail (PII / account-number redaction)
[✓] Resilience4j timeout + circuit breaker on all LLM/RAG calls
[✓] Rate limiting on chat endpoints (per-client, stricter on /agent)
[✓] Kafka async document ingestion — producer confirmation, manual-ack consumer,
    retry + DLT, explicit topic retention policy
[✓] Audit log encryption at rest (AES-GCM)
[✓] Docker (multi-stage build, non-root user, docker-compose for local Kafka)

[ ] Financial account/transaction/analytics tools (real domain data — AccountSpecificActionAgent is currently a stub)
[ ] MCP client (consuming external MCP servers)
[ ] Security / Auth — userId is currently a trusted, unverified client-supplied string;
    JWT audience validation not yet implemented (most significant open security gap)
[ ] User-level / tenant-level data isolation on RAG retrieval and conversation access
[ ] Redis caching
[ ] Broader event-driven processing (guardrail-violation events, agent tool-invocation audit trail)
[ ] Observability stack (structured logging, tracing, guardrail-trigger-rate metrics, dashboards)
[ ] Integration test suite (Testcontainers)
[ ] Kubernetes
[ ] AWS deployment
```

---

# Financial AI Safety Principles

The assistant is designed to provide educational financial information.

It should:

- Clearly distinguish education from personalized financial advice — enforced structurally: `ADVICE_REQUEST` intent bypasses the LLM entirely for a fixed disclaimer response.
- Avoid fabricating account balances or transactions — `AccountSpecificActionAgent` returns an honest "not yet available" response rather than guessing, until real tools and auth exist.
- Never invent financial figures.
- Avoid unsupported investment recommendations.
- Avoid pretending to have access to data it cannot retrieve.
- Ask for clarification when required.
- Use application tools when actual financial data is required.
- Protect user-specific financial information — output guardrail redacts account/card-number-shaped content; audit content is encrypted at rest.
- Keep financial data isolated by user and conversation — **not yet fully enforced**; tenant-scoping remains an open gap (see Current Implementation Status).

---

# Engineering Principles

- Clean separation of concerns
- Dependency inversion
- Small focused services
- Explicit DTOs
- Configuration through environment variables
- Testable business logic
- Production-oriented exception handling
- Observability from the beginning
- Secure handling of financial data
- Incremental architecture evolution
- Defense in depth — guardrails are applied at multiple points (input, retrieved content, output) rather than relying on a single check

The AI layer should not directly own or duplicate financial domain logic. Financial facts should come from trusted application services/tools, while the AI layer focuses on understanding, orchestration, reasoning, and response generation.

---

# Long-Term Vision

```text
                         Finance AI Platform
                                │
             ┌──────────────────┼──────────────────┐
             │                  │                  │
             ▼                  ▼                  ▼
          LLM Layer        Memory Layer        Knowledge
             │            (encrypted)               │
             │                  ▼                  ▼
             │             PostgreSQL             RAG
             │                                (chunk-screened)
             ▼
     Agent Registry / Orchestrator
             │
       ┌─────┼─────┐
       ▼     ▼     ▼
    Tools   MCP  Analytics
       │     │     │
       └─────┼─────┘
             ▼
      Finance Services
```

The objective is not merely to build a chatbot, but to learn and demonstrate how a modern AI service evolves from a simple LLM API into a secure, observable, tool-using, retrieval-enabled, agentic financial platform.

---

## License

This project is intended as a learning and engineering project.