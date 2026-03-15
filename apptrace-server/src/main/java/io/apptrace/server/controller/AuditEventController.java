package io.apptrace.server.controller;

import io.apptrace.server.domain.model.AuditEventEntity;
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