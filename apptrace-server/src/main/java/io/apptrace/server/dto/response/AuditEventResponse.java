package io.apptrace.server.dto.response;

import io.apptrace.server.domain.enums.EventSeverity;
import io.apptrace.server.domain.model.AuditEventEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP response body for a single audit event.
 * Intentionally excludes internal fields like payloadHash and chainHash —
 * those are for integrity verification, not for API consumers.
 */
public record AuditEventResponse(
        UUID id,
        UUID tenantId,
        long sequenceNum,
        String idempotencyKey,
        Actor actor,
        String action,
        Resource resource,
        String category,
        EventSeverity severity,
        Map<String, Object> metadata,
        OffsetDateTime occurredAt,
        OffsetDateTime receivedAt
) {

    public record Actor(String id, String type, String name) {}

    public record Resource(String type, String id) {}

    public static AuditEventResponse from(AuditEventEntity e) {
        return new AuditEventResponse(
                e.getId(),
                e.getTenantId(),
                e.getSequenceNum(),
                e.getIdempotencyKey(),
                new Actor(e.getActorId(), e.getActorType(), e.getActorName()),
                e.getEventType(),
                e.getResourceType() != null
                        ? new Resource(e.getResourceType(), e.getResourceId())
                        : null,
                e.getEventCategory(),
                e.getSeverity(),
                e.getMetadata(),
                e.getOccurredAt(),
                e.getReceivedAt()
        );
    }
}