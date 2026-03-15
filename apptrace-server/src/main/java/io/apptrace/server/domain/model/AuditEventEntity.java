package io.apptrace.server.domain.model;

import io.apptrace.server.domain.enums.EventSeverity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * An immutable audit event — the core entity of AppTrace.
 * <p/>
 * Immutability is enforced at three layers:
 *   1. {@code @Immutable} — Hibernate skips dirty-checking entirely
 *   2. All columns marked {@code updatable = false}
 *   3. DB-level RULE prevents UPDATE/DELETE reaching Postgres (V2 migration)
 * <p/>
 * Hash chain:
 *   payloadHash = SHA-256(sequenceNum | tenantId | actorId | eventType | payload)
 *   chainHash   = SHA-256(prevChainHash | payloadHash)
 */
@Entity
@Getter
@Immutable
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "sequence_num", nullable = false, updatable = false)
    private long sequenceNum;

    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;

    // -- Actor --
    @Column(name = "actor_type", nullable = false, updatable = false, length = 64)
    private String actorType;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 256)
    private String actorId;

    @Column(name = "actor_name", updatable = false)
    private String actorName;

    // -- Action --
    @Column(name = "event_type", nullable = false, updatable = false, length = 128)
    private String eventType;

    @Column(name = "event_category", nullable = false, updatable = false, length = 64)
    private String eventCategory = "GENERAL";

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, updatable = false, length = 16)
    private EventSeverity severity = EventSeverity.INFO;

    // -- Resource --
    @Column(name = "resource_type", updatable = false, length = 128)
    private String resourceType;

    @Column(name = "resource_id", updatable = false, length = 256)
    private String resourceId;

    // -- Payload --
    @Type(JsonBinaryType.class)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // -- Integrity --
    @Column(name = "payload_hash", nullable = false, updatable = false, length = 64)
    private String payloadHash;

    @Column(name = "chain_hash", nullable = false, updatable = false, length = 64)
    private String chainHash;

    // -- Timestamps --
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "received_at", updatable = false, insertable = false)
    private OffsetDateTime receivedAt;

    // -- Source context --
    @Column(name = "service_name",    updatable = false, length = 128)
    private String serviceName;

    @Column(name = "service_version", updatable = false, length = 32)
    private String serviceVersion;

    @Column(name = "environment",     updatable = false, length = 32)
    private String environment;

    @Column(name = "trace_id",        updatable = false, length = 128)
    private String traceId;

    @Column(name = "request_id",      updatable = false, length = 128)
    private String requestId;

    protected AuditEventEntity() {}

    private AuditEventEntity(Builder b) {
        this.tenantId       = b.tenantId;
        this.sequenceNum    = b.sequenceNum;
        this.idempotencyKey = b.idempotencyKey;
        this.actorType      = b.actorType;
        this.actorId        = b.actorId;
        this.actorName      = b.actorName;
        this.eventType      = b.eventType;
        this.eventCategory  = b.eventCategory != null ? b.eventCategory : "GENERAL";
        this.severity       = b.severity      != null ? b.severity      : EventSeverity.INFO;
        this.resourceType   = b.resourceType;
        this.resourceId     = b.resourceId;
        this.payload        = b.payload   != null ? Map.copyOf(b.payload)   : Map.of();
        this.metadata       = b.metadata  != null ? Map.copyOf(b.metadata)  : Map.of();
        this.payloadHash    = b.payloadHash;
        this.chainHash      = b.chainHash;
        this.occurredAt     = b.occurredAt;
        this.serviceName    = b.serviceName;
        this.serviceVersion = b.serviceVersion;
        this.environment    = b.environment;
        this.traceId        = b.traceId;
        this.requestId      = b.requestId;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID tenantId;
        private long sequenceNum;
        private String idempotencyKey;
        private String actorType, actorId, actorName;
        private String eventType, eventCategory;
        private EventSeverity severity;
        private String resourceType, resourceId;
        private Map<String, Object> payload, metadata;
        private String payloadHash, chainHash;
        private OffsetDateTime occurredAt;
        private String serviceName, serviceVersion, environment, traceId, requestId;

        public Builder tenantId(UUID v)                 { this.tenantId       = v; return this; }
        public Builder sequenceNum(long v)              { this.sequenceNum    = v; return this; }
        public Builder idempotencyKey(String v)         { this.idempotencyKey    = v; return this; }
        public Builder actorType(String v)              { this.actorType      = v; return this; }
        public Builder actorId(String v)                { this.actorId        = v; return this; }
        public Builder actorName(String v)              { this.actorName      = v; return this; }
        public Builder eventType(String v)              { this.eventType      = v; return this; }
        public Builder eventCategory(String v)          { this.eventCategory  = v; return this; }
        public Builder severity(EventSeverity v)        { this.severity       = v; return this; }
        public Builder resourceType(String v)           { this.resourceType   = v; return this; }
        public Builder resourceId(String v)             { this.resourceId     = v; return this; }
        public Builder payload(Map<String, Object> v)   { this.payload        = v; return this; }
        public Builder metadata(Map<String, Object> v)  { this.metadata       = v; return this; }
        public Builder payloadHash(String v)            { this.payloadHash    = v; return this; }
        public Builder chainHash(String v)              { this.chainHash      = v; return this; }
        public Builder occurredAt(OffsetDateTime v)     { this.occurredAt     = v; return this; }
        public Builder serviceName(String v)            { this.serviceName    = v; return this; }
        public Builder serviceVersion(String v)         { this.serviceVersion = v; return this; }
        public Builder environment(String v)            { this.environment    = v; return this; }
        public Builder traceId(String v)                { this.traceId        = v; return this; }
        public Builder requestId(String v)              { this.requestId      = v; return this; }

        public AuditEventEntity build() {
            if (tenantId    == null) throw new IllegalArgumentException("tenantId is required");
            if (actorType   == null) throw new IllegalArgumentException("actorType is required");
            if (actorId     == null) throw new IllegalArgumentException("actorId is required");
            if (eventType   == null) throw new IllegalArgumentException("eventType is required");
            if (payloadHash == null) throw new IllegalArgumentException("payloadHash is required");
            if (chainHash   == null) throw new IllegalArgumentException("chainHash is required");
            if (occurredAt  == null) throw new IllegalArgumentException("occurredAt is required");
            return new AuditEventEntity(this);
        }
    }
}

