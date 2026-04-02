# AppTrace

An open-source, append-only audit log microservice. Provides a tamper-evident, cryptographically chained audit trail for multi-tenant applications.

## Overview

AppTrace is a Maven multi-module project:

| Module | Purpose |
|--------|---------|
| `apptrace-server` | Standalone Spring Boot REST microservice |
| `apptrace-spring-boot-starter` | Auto-configuration client SDK for Spring Boot applications |

**Stack:** Java 25, Spring Boot 4, PostgreSQL 16, Flyway, Hibernate/JPA, Testcontainers, Lombok

---

## Features

- **Append-only enforcement** — immutability at three layers: Hibernate `@Immutable`, `updatable = false` columns, and PostgreSQL RULEs that block `UPDATE`/`DELETE`
- **Cryptographic hash chain** — every event is linked; the log is tamper-evident and verifiable
- **Multi-tenancy** — full tenant isolation; every request authenticated via scoped Bearer API keys
- **Idempotent ingestion** — client-supplied `idempotencyKey` prevents duplicate events on retry
- **Keyset pagination** — cursor-based (not offset), efficient for large append-only logs
- **Batch ingestion** — ingest up to 100 events in a single request (207 Multi-Status response)
- **Optimistic concurrency** — concurrent ingestion handled without serialization via `@Version` + `@Retryable`

---

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Java 25+
- Maven Wrapper (`./mvnw`) included

### Run with Docker Compose

```bash
# Start PostgreSQL + AppTrace server
docker-compose up -d
```

The server starts at `http://localhost:8080`.

### Run Locally (Development)

```bash
# Start PostgreSQL only
docker-compose up -d postgres

# Build all modules
./mvnw clean package

# Run the server
./mvnw spring-boot:run -pl apptrace-server
```

### Run Tests

```bash
# All tests (requires Docker for Testcontainers)
./mvnw test

# Server module only
./mvnw test -pl apptrace-server

# Single test class
./mvnw test -pl apptrace-server -Dtest=IngestionIntegrationTest
```

---

## Architecture

### Multi-Tenancy and Authentication

Every request is authenticated via a **Bearer API key** (`Authorization: Bearer at_...`). Keys are 256-bit random values prefixed with `at_`. The plaintext key is returned once at creation — only a SHA-256 hash is stored. Keys carry scopes (`WRITE`, `READ`) and can be revoked or set to expire.

The authenticated key's `tenantId` is stored in `ApiKeyContext` (thread-local) so downstream code never needs a second DB lookup.

Endpoints excluded from API key auth:
- `POST /api/v1/tenants` — tenant creation
- `GET /api/v1/tenants/{id}`
- `POST|GET|DELETE /api/v1/tenants/{id}/api-keys`
- `GET /actuator/health`

### Hash Chain Integrity

Each ingested event extends a per-tenant cryptographic chain:

```
payloadHash = SHA-256(sequenceNum | tenantId | actorId | eventType | canonicalJson(payload))
chainHash   = SHA-256(prevChainHash | payloadHash)
```

The chain is seeded per tenant with `SHA-256("GENESIS")`. Canonical JSON uses lexicographically sorted keys for deterministic hashing. The PostgreSQL function `fn_verify_chain(tenant_id)` can reconstruct and verify the chain at any time.

> **Warning:** Changing `HashService.canonicalJson()` breaks verification of all existing events.

Concurrent ingestion is safe: `HashChainEntity` uses `@Version` optimistic locking, and `AuditEventService` is annotated `@Retryable(OptimisticLockingFailureException)`.

### Append-Only Enforcement (3 Layers)

1. Hibernate `@Immutable` — skips dirty-checking entirely
2. All `AuditEventEntity` columns marked `updatable = false`
3. PostgreSQL `RULE` in V2 migration blocks `UPDATE` and `DELETE` at the database level

### Keyset Pagination

List queries return an opaque Base64url cursor encoding the last returned `sequenceNum`. The server fetches `pageSize + 1` rows; if the result exceeds `pageSize`, a next-page cursor is included in the response.

---

## REST API

Base path: `http://localhost:8080`

### Tenant Management

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/tenants` | None | Create a tenant |
| `GET` | `/api/v1/tenants/{id}` | None | Get tenant by UUID |

**Create tenant:**
```http
POST /api/v1/tenants
Content-Type: application/json

{
  "externalId": "acme-corp",
  "displayName": "Acme Corporation"
}
```

### API Key Management

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/tenants/{tenantId}/api-keys` | None | Create API key (returns plaintext once) |
| `GET` | `/api/v1/tenants/{tenantId}/api-keys` | None | List keys for tenant |
| `DELETE` | `/api/v1/tenants/{tenantId}/api-keys/{keyId}` | None | Revoke a key |

**Create API key:**
```http
POST /api/v1/tenants/{tenantId}/api-keys
Content-Type: application/json

{
  "description": "Production write key",
  "scopes": ["WRITE"],
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

Response includes `plainTextKey` — store this securely, it will not be shown again.

### Audit Events

All endpoints require `Authorization: Bearer <api-key>`.

| Method | Path | Scope | Description |
|--------|------|-------|-------------|
| `POST` | `/api/v1/events` | `WRITE` | Ingest a single event |
| `POST` | `/api/v1/events/batch` | `WRITE` | Ingest up to 100 events (207 Multi-Status) |
| `GET` | `/api/v1/events` | `READ` | List events with pagination and filtering |
| `GET` | `/api/v1/events/{eventId}` | `READ` | Get a single event by UUID |

**Ingest a single event:**
```http
POST /api/v1/events
Authorization: Bearer at_...
Content-Type: application/json

{
  "actorId": "user-123",
  "actorType": "user",
  "actorName": "Jane Doe",
  "eventType": "order.created",
  "eventCategory": "commerce",
  "severity": "INFO",
  "resourceType": "order",
  "resourceId": "order-789",
  "payload": { "amount": 99.99, "currency": "USD" },
  "metadata": { "ip": "1.2.3.4" },
  "occurredAt": "2026-04-02T10:00:00Z",
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "serviceName": "checkout-service",
  "environment": "production",
  "traceId": "abc123"
}
```

**List events with filtering:**
```
GET /api/v1/events?actorId=user-123&pageSize=20&cursor=<opaque>
GET /api/v1/events?resourceType=order&resourceId=order-789
GET /api/v1/events?from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z
```

**Severity values:** `DEBUG`, `INFO`, `WARN`, `ERROR`, `CRITICAL`

---

## Database Schema

Managed by Flyway (`ddl-auto: validate`). Migrations in `apptrace-server/src/main/resources/db/migration/`:

| Migration | Description |
|-----------|-------------|
| `V1` | `tenants`, `api_keys` tables |
| `V2` | `audit_events`, `hash_chain` tables + append-only RULEs |
| `V3` | `fn_genesis_hash()`, `fn_init_hash_chain()`, `fn_verify_chain()` |
| `V4` | API key scopes stored as PostgreSQL `TEXT[]` |
| `V5` | `idempotency_key` column with per-tenant unique index |

---

## Configuration

### Server (`application.yaml`)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/apptrace
    username: apptrace
    password: apptrace
    hikari:
      maximum-pool-size: 10
  jpa:
    hibernate:
      ddl-auto: validate
server:
  port: 8080
```

Override via environment variables when running in Docker:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/apptrace
SPRING_DATASOURCE_USERNAME=apptrace
SPRING_DATASOURCE_PASSWORD=apptrace
```

---

## Client SDK (Spring Boot Starter)

Add the starter to your Spring Boot application to send audit events without boilerplate HTTP code.

### Dependency

```xml
<dependency>
    <groupId>io.apptrace</groupId>
    <artifactId>apptrace-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### Configuration

```yaml
apptrace:
  server-url: https://apptrace.yourcompany.com
  api-key: at_live_abc123xyz
  enabled: true          # set false to disable without removing the dependency
  connect-timeout: 5s
  read-timeout: 10s
```

Auto-configuration activates when `apptrace.server-url` is set.

### Usage

```java
@Autowired
AppTraceClient appTraceClient;

// Single event
appTraceClient.record(b -> b
    .actor("user-123", "user", "Jane Doe")
    .action("order.created")
    .resource("order", "order-789")
    .severity(EventSeverity.INFO)
    .payload(Map.of("amount", 99.99))
    .idempotencyKey("550e8400-e29b-41d4-a716-446655440000")
);

// Batch
appTraceClient.recordBatch(List.of(
    b -> b.actor("user-1", "user").action("login"),
    b -> b.actor("user-2", "user").action("logout")
));
```

HTTP errors are logged but swallowed — audit logging will never break your application's main flow.

---

## Postman Collection

Ready-to-use Postman collection and local environment are included in the `postman/` directory:

- `apptrace_collection.json` — full API request examples
- `apptrace_local_environment.json` — pre-configured for `http://localhost:8080`

---

## Project Structure

```
apptrace/
├── apptrace-server/              # REST microservice
│   └── src/main/
│       ├── java/io/apptrace/server/
│       │   ├── controller/       # TenantController, ApiKeyController, AuditEventController
│       │   ├── service/          # AuditEventService, HashService, ApiKeyService
│       │   ├── domain/model/     # JPA entities (AuditEventEntity, HashChainEntity, ...)
│       │   ├── security/         # ApiKeyAuthFilter, ApiKeyContext, SecurityConfig
│       │   └── dto/              # Request/response DTOs
│       └── resources/db/migration/  # Flyway migrations V1–V5
├── apptrace-spring-boot-starter/ # Client auto-configuration SDK
│   └── src/main/java/io/apptrace/starter/
│       ├── AppTraceAutoConfiguration.java
│       ├── AppTraceClient.java
│       └── AppTraceProperties.java
├── postman/                      # Postman collection & environment
├── init-scripts/                 # PostgreSQL init SQL
├── Dockerfile
└── docker-compose.yml
```

---

## License

Apache 2.0
