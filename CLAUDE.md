# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AppTrace is an open-source append-only audit log microservice. It consists of two Maven modules:

- **`apptrace-server`** — the standalone Spring Boot REST microservice
- **`apptrace-spring-boot-starter`** — a client SDK (auto-configuration) for Spring Boot apps to send audit events

Stack: Java 25, Spring Boot 4, PostgreSQL, Flyway, Hibernate/JPA, Testcontainers, Lombok.

## Commands

```bash
# Start PostgreSQL (required to run the server or integration tests locally)
docker-compose up -d

# Build all modules
./mvnw clean package

# Run all tests (uses Testcontainers — Docker must be running)
./mvnw test

# Run tests for a specific module
./mvnw test -pl apptrace-server

# Run a single test class
./mvnw test -pl apptrace-server -Dtest=IngestionIntegrationTest

# Run the server (requires Docker Compose Postgres to be up)
./mvnw spring-boot:run -pl apptrace-server
```

## Architecture

### Multi-tenancy and Authentication

Every request to the server is authenticated via a **Bearer API key** (`ApiKeyAuthFilter`). The key is looked up by SHA-256 hash — the plaintext key is shown once at creation and never stored. On success, the resolved `ApiKeyEntity` (including `tenantId`) is stored in `ApiKeyContext` (a thread-local) so controllers can read it without a second DB query.

API keys have scopes (`WRITE`, `READ`) and the tenant/API key management endpoints (`/api/v1/tenants`, `/api/v1/api-keys`) are excluded from API key auth.

### Hash Chain Integrity

Every audit event is part of a per-tenant cryptographic hash chain. This makes the log tamper-evident:

- `payloadHash = SHA-256(sequenceNum|tenantId|actorId|eventType|canonicalJson(payload))`
- `chainHash   = SHA-256(prevChainHash|payloadHash)`
- Each tenant's chain is seeded with `SHA-256("GENESIS")`

The `hash_chain` table holds one row per tenant tracking the chain head (`lastChainHash`, `lastSequenceNum`). Concurrent ingestion is handled with **optimistic locking** (`@Version` on `HashChainEntity`) and `@Retryable(OptimisticLockingFailureException)` in `AuditEventService`.

The canonical payload hash format is mirrored by `fn_compute_payload_hash()` in the V3 Flyway migration — **changing `HashService.canonicalJson()` breaks all existing chain verification**.

### Append-Only Enforcement

`AuditEventEntity` is immutable at three layers:
1. `@Immutable` (Hibernate skips dirty-checking)
2. All columns have `updatable = false`
3. A PostgreSQL `RULE` in the V2 migration blocks `UPDATE`/`DELETE` at the DB level

### Pagination

Queries use **keyset pagination** via an opaque Base64url-encoded cursor (`Cursor` record) wrapping a `sequence_num`. The pattern is: fetch `pageSize + 1` rows; if `results.size() > pageSize`, there is a next page and the cursor encodes the last returned `sequenceNum`.

### Database Schema

Managed by Flyway (`ddl-auto: validate`). Migrations live in `apptrace-server/src/main/resources/db/migration/`:
- `V1` — `tenants`, `api_keys`
- `V2` — `audit_events`, `hash_chain` (with append-only rules)
- `V3` — helper SQL functions and triggers (including `fn_compute_payload_hash`)
- `V4` — scopes stored as a PostgreSQL array
- `V5` — `idempotency_key` column on `audit_events`

### Client SDK (`apptrace-spring-boot-starter`)

Auto-configured when `apptrace.server-url` is set. Creates an `AppTraceClient` bean. Errors from the HTTP calls are intentionally swallowed — audit logging must never break the calling application's main flow. Set `apptrace.enabled: false` to disable without removing the dependency.

Required properties:
```yaml
apptrace:
  server-url: https://apptrace.example.com
  api-key: at_live_...
```
