package io.apptrace.server.controller;

import io.apptrace.server.domain.model.AuditEventEntity;
import io.apptrace.server.domain.model.Page;
import io.apptrace.server.dto.request.BatchIngestRequest;
import io.apptrace.server.dto.request.IngestRequest;
import io.apptrace.server.dto.response.AuditEventResponse;
import io.apptrace.server.dto.response.BatchIngestResponse;
import io.apptrace.server.security.ApiKeyContext;
import io.apptrace.server.service.AuditEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/events")
public class AuditEventController {

    private final AuditEventService auditEventService;

    /**
     * Ingest a single audit event.
     *
     * Returns 201 Created with the saved event.
     * Returns 200 OK if idempotency key already exists (no duplicate created).
     */
    @PostMapping
    public org.springframework.http.ResponseEntity<AuditEventResponse> ingest(
            @Valid @RequestBody IngestRequest request,
            HttpServletRequest httpRequest
    ) {
        UUID tenantId = ApiKeyContext.getTenantId();

        // Enrich actor IP from HTTP request if not provided by caller
        IngestRequest enriched = enrichWithIp(request, httpRequest);

        AuditEventEntity event = auditEventService.record(tenantId, enriched);

        // 200 if idempotency key matched existing event, 201 if newly created
        boolean isDuplicate = request.idempotencyKey() != null
                && request.idempotencyKey().equals(event.getIdempotencyKey())
                && event.getReceivedAt() != null
                && event.getReceivedAt().isBefore(java.time.OffsetDateTime.now().minusSeconds(1));

        HttpStatus status = isDuplicate ? HttpStatus.OK : HttpStatus.CREATED;
        return org.springframework.http.ResponseEntity
                .status(status)
                .body(AuditEventResponse.from(event));
    }

    /**
     * Ingest a batch of audit events (max 100).
     *
     * Events in a batch are processed sequentially to maintain chain order.
     * Idempotency is handled per-event within the batch.
     *
     * Returns 207 Multi-Status with results for each event.
     */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchIngestResponse ingestBatch(
            @Valid @RequestBody BatchIngestRequest batchRequest,
            HttpServletRequest httpRequest
    ) {
        UUID tenantId = ApiKeyContext.getTenantId();
        List<AuditEventResponse> responses = new ArrayList<>();
        int duplicates = 0;

        for (IngestRequest request : batchRequest.events()) {
            IngestRequest enriched = enrichWithIp(request, httpRequest);
            AuditEventEntity event = auditEventService.record(tenantId, enriched);
            responses.add(AuditEventResponse.from(event));

            if (request.idempotencyKey() != null
                    && request.idempotencyKey().equals(event.getIdempotencyKey())) {
                duplicates++;
            }
        }

        return BatchIngestResponse.of(responses, duplicates);
    }

// -------------------------------------------------------------------------
    // QUERYING
    // -------------------------------------------------------------------------

    /**
     * Lists audit events with optional filters.
     *
     * Filter priority (only one applies at a time):
     *   1. actorId — all events by a specific actor
     *   2. resourceType + resourceId — all events on a specific resource
     *   3. from + to — all events in a time range
     *   4. no filter — all events for the tenant
     *
     * Always cursor-paginated. Pass the nextCursor from the response as
     * the cursor param on the next request.
     */
    @GetMapping
    public Page<AuditEventResponse> list(
            @RequestParam(defaultValue = "")  String cursor,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to
    ) {
        UUID tenantId = ApiKeyContext.getTenantId();

        if (actorId != null) {
            return auditEventService
                    .findPageByActor(tenantId, actorId, cursor, pageSize)
                    .map(AuditEventResponse::from);
        }
        if (resourceType != null && resourceId != null) {
            return auditEventService
                    .findPageByResource(tenantId, resourceType, resourceId, cursor, pageSize)
                    .map(AuditEventResponse::from);
        }
        if (from != null && to != null) {
            return auditEventService
                    .findPageByTimeRange(tenantId, from, to, cursor, pageSize)
                    .map(AuditEventResponse::from);
        }
        return auditEventService
                .findPage(tenantId, cursor, pageSize)
                .map(AuditEventResponse::from);
    }

    @GetMapping("/{eventId}")
    public AuditEventResponse getById(@PathVariable UUID eventId) {
        UUID tenantId = ApiKeyContext.getTenantId();
        return AuditEventResponse.from(auditEventService.findById(tenantId, eventId));
    }

    // -------------------------------------------------------------------------
    // Enrichment
    // -------------------------------------------------------------------------

    /**
     * If the caller didn't provide actor.ip, extract it from the HTTP request.
     * Checks X-Forwarded-For first (for requests behind a proxy), then falls
     * back to the direct remote address.
     */
    private IngestRequest enrichWithIp(IngestRequest request, HttpServletRequest httpRequest) {
        if (request.actor().ip() != null) return request;

        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = httpRequest.getRemoteAddr();
        } else {
            // X-Forwarded-For can be a comma-separated list — first entry is the client
            ip = ip.split(",")[0].strip();
        }

        String resolvedIp = ip;
        return new IngestRequest(
                request.idempotencyKey(),
                new IngestRequest.Actor(
                        request.actor().id(),
                        request.actor().type(),
                        request.actor().name(),
                        resolvedIp
                ),
                request.action(),
                request.resource(),
                request.category(),
                request.severity(),
                request.metadata(),
                request.occurredAt(),
                request.serviceName(),
                request.serviceVersion(),
                request.environment(),
                request.traceId(),
                request.requestId()
        );
    }
}