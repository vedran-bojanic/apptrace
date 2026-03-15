package io.apptrace.server.service;

import io.apptrace.server.domain.enums.EventSeverity;
import io.apptrace.server.domain.model.AuditEventEntity;
import io.apptrace.server.domain.model.Cursor;
import io.apptrace.server.domain.model.HashChainEntity;
import io.apptrace.server.domain.model.Page;
import io.apptrace.server.dto.request.IngestRequest;
import io.apptrace.server.exception.ChainIntegrityException;
import io.apptrace.server.exception.ResourceNotFoundException;
import io.apptrace.server.repository.AuditEventRepository;
import io.apptrace.server.repository.HashChainRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuditEventService {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditEventRepository eventRepository;
    private final HashChainRepository  chainRepository;
    private final HashService          hashService;

    public AuditEventService(
            AuditEventRepository eventRepository,
            HashChainRepository chainRepository,
            HashService hashService
    ) {
        this.eventRepository = eventRepository;
        this.chainRepository = chainRepository;
        this.hashService     = hashService;
    }

    // -------------------------------------------------------------------------
    // INGESTION
    // -------------------------------------------------------------------------

    /**
     * Records a single audit event for a tenant.
     *
     * Handles idempotency — if a key is provided and already exists for this
     * tenant, returns the existing event without creating a duplicate.
     *
     * @Retryable handles optimistic lock collisions when two events arrive
     * simultaneously for the same tenant.
     */
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    @Transactional
    public AuditEventEntity record(UUID tenantId, IngestRequest request) {

        // Idempotency check — return existing event if key already used
        if (request.idempotencyKey() != null) {
            Optional<AuditEventEntity> existing =
                    eventRepository.findByTenantIdAndIdempotencyKey(
                            tenantId, request.idempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // Load chain head
        HashChainEntity chain = chainRepository.findById(tenantId)
                .orElseThrow(() -> new ChainIntegrityException(
                        "No hash chain for tenant: " + tenantId));

        long sequenceNum = chain.nextSequenceNum();

        // Compute hashes
        Map<String, Object> payload = buildPayload(request);
        String payloadHash = hashService.computePayloadHash(
                sequenceNum, tenantId,
                request.actor().id(), request.action(),
                payload);
        String chainHash = hashService.computeChainHash(
                chain.getLastChainHash(), payloadHash);

        // Build and save event
        AuditEventEntity event = AuditEventEntity.builder()
                .tenantId(tenantId)
                .sequenceNum(sequenceNum)
                .idempotencyKey(request.idempotencyKey())
                .actorId(request.actor().id())
                .actorType(request.actor().type())
                .actorName(request.actor().name())
                .eventType(request.action())
                .eventCategory(request.category() != null ? request.category() : "GENERAL")
                .severity(request.severity() != null ? request.severity() : EventSeverity.INFO)
                .resourceType(request.resource() != null ? request.resource().type() : null)
                .resourceId(request.resource()  != null ? request.resource().id()   : null)
                .payload(payload)
                .metadata(request.metadata() != null ? request.metadata() : Map.of())
                .payloadHash(payloadHash)
                .chainHash(chainHash)
                .occurredAt(request.occurredAt() != null ? request.occurredAt() : OffsetDateTime.now())
                .serviceName(request.serviceName())
                .serviceVersion(request.serviceVersion())
                .environment(request.environment())
                .traceId(request.traceId())
                .requestId(request.requestId())
                .build();

        AuditEventEntity saved = eventRepository.save(event);

        // Advance chain head — @Version will throw OptimisticLockingFailureException
        // if another thread beat us, triggering a @Retryable retry
        chain.advance(sequenceNum, chainHash, saved.getId());
        chainRepository.save(chain);

        return saved;
    }

    // -------------------------------------------------------------------------
    // QUERIES
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<AuditEventEntity> findPage(
            UUID tenantId, String encodedCursor, int pageSize) {
        pageSize = clamp(pageSize);
        long afterSeq = decodeCursor(encodedCursor);
        List<AuditEventEntity> results = eventRepository.findPage(
                tenantId, afterSeq, PageRequest.of(0, pageSize + 1));
        return Page.from(results, pageSize, e -> new Cursor(e.getSequenceNum()));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventEntity> findPageByActor(
            UUID tenantId, String actorId, String encodedCursor, int pageSize) {
        pageSize = clamp(pageSize);
        long afterSeq = decodeCursor(encodedCursor);
        List<AuditEventEntity> results = eventRepository.findPageByActor(
                tenantId, actorId, afterSeq, PageRequest.of(0, pageSize + 1));
        return Page.from(results, pageSize, e -> new Cursor(e.getSequenceNum()));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventEntity> findPageByResource(
            UUID tenantId, String resourceType, String resourceId,
            String encodedCursor, int pageSize) {
        pageSize = clamp(pageSize);
        long afterSeq = decodeCursor(encodedCursor);
        List<AuditEventEntity> results = eventRepository.findPageByResource(
                tenantId, resourceType, resourceId, afterSeq,
                PageRequest.of(0, pageSize + 1));
        return Page.from(results, pageSize, e -> new Cursor(e.getSequenceNum()));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventEntity> findPageByTimeRange(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to,
            String encodedCursor, int pageSize) {
        pageSize = clamp(pageSize);
        long afterSeq = decodeCursor(encodedCursor);
        List<AuditEventEntity> results = eventRepository.findPageByTimeRange(
                tenantId, from, to, afterSeq, PageRequest.of(0, pageSize + 1));
        return Page.from(results, pageSize, e -> new Cursor(e.getSequenceNum()));
    }

    @Transactional(readOnly = true)
    public AuditEventEntity findById(UUID tenantId, UUID eventId) {
        AuditEventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found: " + eventId));
        // Don't leak that the event exists for another tenant
        if (!event.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }
        return event;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the canonical payload map from the request.
     * The actor IP (enriched from HTTP request in the controller) is included here.
     */
    private Map<String, Object> buildPayload(IngestRequest request) {
        var actor = request.actor();
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("actorId",   actor.id());
        payload.put("actorType", actor.type());
        if (actor.ip() != null) payload.put("actorIp", actor.ip());
        payload.put("action",    request.action());
        if (request.resource() != null) {
            payload.put("resourceType", request.resource().type());
            payload.put("resourceId",   request.resource().id());
        }
        return payload;
    }

    private long decodeCursor(String encodedCursor) {
        if (encodedCursor == null || encodedCursor.isBlank())
            return Cursor.BEGINNING.sequenceNum();
        return Cursor.decode(encodedCursor).sequenceNum();
    }

    private int clamp(int requested) {
        if (requested <= 0)            return 50;
        if (requested > MAX_PAGE_SIZE) return MAX_PAGE_SIZE;
        return requested;
    }
}
