package io.apptrace.server.service;

import io.apptrace.server.domain.enums.EventSeverity;
import io.apptrace.server.domain.model.AuditEventEntity;
import io.apptrace.server.domain.model.Cursor;
import io.apptrace.server.domain.model.HashChainEntity;
import io.apptrace.server.domain.model.Page;
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
     * Records a new audit event for a tenant.
     *
     * This is the hot path — called on every incoming event.
     *
     * Steps:
     *   1. Load the chain head (with optimistic lock via @Version)
     *   2. Assign the next sequence number
     *   3. Compute payloadHash and chainHash
     *   4. Save the event
     *   5. Advance the chain head
     *
     * If two events arrive simultaneously for the same tenant, one will get
     * an OptimisticLockingFailureException on step 5. @Retryable catches it
     * and retries the whole transaction automatically (up to 3 times).
     */
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    @Transactional
    public AuditEventEntity record(IngestRequest request) {
        // 1. Load chain head
        HashChainEntity chain = chainRepository.findById(request.tenantId())
                .orElseThrow(() -> new ChainIntegrityException(
                        "No hash chain found for tenant: " + request.tenantId()
                                + " — was the tenant created properly?"));

        // 2. Next sequence number
        long sequenceNum = chain.nextSequenceNum();

        // 3. Compute hashes
        String payloadHash = hashService.computePayloadHash(
                sequenceNum,
                request.tenantId(),
                request.actorId(),
                request.eventType(),
                request.payload()
        );
        String chainHash = hashService.computeChainHash(
                chain.getLastChainHash(),
                payloadHash
        );

        // 4. Build and save the event
        AuditEventEntity event = AuditEventEntity.builder()
                .tenantId(request.tenantId())
                .sequenceNum(sequenceNum)
                .actorType(request.actorType())
                .actorId(request.actorId())
                .actorName(request.actorName())
                .eventType(request.eventType())
                .eventCategory(request.eventCategory())
                .severity(request.severity() != null ? request.severity() : EventSeverity.INFO)
                .resourceType(request.resourceType())
                .resourceId(request.resourceId())
                .payload(request.payload() != null ? request.payload() : Map.of())
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

        // 5. Advance chain head — @Version here will throw OptimisticLockingFailureException
        //    if another thread beat us to it, triggering a @Retryable retry
        chain.advance(sequenceNum, chainHash, saved.getId());
        chainRepository.save(chain);

        return saved;
    }

    // -------------------------------------------------------------------------
    // QUERIES
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<AuditEventEntity> findPage(UUID tenantId, String encodedCursor, int pageSize) {
        pageSize = clampPageSize(pageSize);
        long afterSeq = decodeCursor(encodedCursor);
        // Fetch one extra row to detect if there's a next page
        List<AuditEventEntity> results = eventRepository.findPage(
                tenantId, afterSeq, PageRequest.of(0, pageSize + 1));
        return Page.from(results, pageSize,
                e -> new Cursor(e.getSequenceNum()));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventEntity> findPageByActor(
            UUID tenantId, String actorId, String encodedCursor, int pageSize) {
        pageSize = clampPageSize(pageSize);
        long afterSeq = decodeCursor(encodedCursor);
        List<AuditEventEntity> results = eventRepository.findPageByActor(
                tenantId, actorId, afterSeq, PageRequest.of(0, pageSize + 1));
        return Page.from(results, pageSize, e -> new Cursor(e.getSequenceNum()));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventEntity> findPageByResource(
            UUID tenantId, String resourceType, String resourceId,
            String encodedCursor, int pageSize) {
        pageSize = clampPageSize(pageSize);
        long afterSeq = decodeCursor(encodedCursor);
        List<AuditEventEntity> results = eventRepository.findPageByResource(
                tenantId, resourceType, resourceId, afterSeq, PageRequest.of(0, pageSize + 1));
        return Page.from(results, pageSize, e -> new Cursor(e.getSequenceNum()));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventEntity> findPageByTimeRange(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to,
            String encodedCursor, int pageSize) {
        pageSize = clampPageSize(pageSize);
        long afterSeq = decodeCursor(encodedCursor);
        List<AuditEventEntity> results = eventRepository.findPageByTimeRange(
                tenantId, from, to, afterSeq, PageRequest.of(0, pageSize + 1));
        return Page.from(results, pageSize, e -> new Cursor(e.getSequenceNum()));
    }

    @Transactional(readOnly = true)
    public AuditEventEntity findById(UUID tenantId, UUID eventId) {
        AuditEventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (!event.getTenantId().equals(tenantId)) {
            // Don't leak that the event exists for another tenant
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }
        return event;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private long decodeCursor(String encodedCursor) {
        if (encodedCursor == null || encodedCursor.isBlank()) {
            return Cursor.BEGINNING.sequenceNum();
        }
        return Cursor.decode(encodedCursor).sequenceNum();
    }

    private int clampPageSize(int requested) {
        if (requested <= 0)            return 50;
        if (requested > MAX_PAGE_SIZE) return MAX_PAGE_SIZE;
        return requested;
    }

    // -------------------------------------------------------------------------
    // Ingest request — passed by the controller into record()
    // -------------------------------------------------------------------------

    public record IngestRequest(
            UUID tenantId,
            String actorType,
            String actorId,
            String actorName,
            String eventType,
            String eventCategory,
            EventSeverity severity,
            String resourceType,
            String resourceId,
            Map<String, Object> payload,
            Map<String, Object> metadata,
            OffsetDateTime occurredAt,
            String serviceName,
            String serviceVersion,
            String environment,
            String traceId,
            String requestId
    ) {}
}

