package io.apptrace.server.repository;

import io.apptrace.server.domain.model.AuditEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    /**
     * Core cursor-based pagination query.
     * <p/>
     * Fetches pageSize+1 rows — if we get pageSize+1 back, there is a next page.
     * The caller trims the extra row and uses the last item's sequenceNum as the next cursor.
     * <p/>
     * Example: cursor=1050, pageSize=50 → fetch rows 1051-1101, return 1051-1100 + cursor pointing to 1100
     */
    @Query("""
        SELECT e FROM AuditEventEntity e
        WHERE e.tenantId = :tenantId
          AND e.sequenceNum > :afterSequenceNum
        ORDER BY e.sequenceNum ASC
    """)
    List<AuditEventEntity> findPage(
            @Param("tenantId") UUID tenantId,
            @Param("afterSequenceNum") long afterSequenceNum,
            Pageable pageable
    );

    /**
     * Same as findPage but filtered by actor.
     * e.g. "show me everything user-123 ever did"
     */
    @Query("""
        SELECT e FROM AuditEventEntity e
        WHERE e.tenantId = :tenantId
          AND e.actorId = :actorId
          AND e.sequenceNum > :afterSequenceNum
        ORDER BY e.sequenceNum ASC
    """)
    List<AuditEventEntity> findPageByActor(
            @Param("tenantId") UUID tenantId,
            @Param("actorId") String actorId,
            @Param("afterSequenceNum") long afterSequenceNum,
            Pageable pageable
    );

    /**
     * Filtered by resource.
     * e.g. "show me everything that happened to ride-456"
     */
    @Query("""
        SELECT e FROM AuditEventEntity e
        WHERE e.tenantId = :tenantId
          AND e.resourceType = :resourceType
          AND e.resourceId = :resourceId
          AND e.sequenceNum > :afterSequenceNum
        ORDER BY e.sequenceNum ASC
    """)
    List<AuditEventEntity> findPageByResource(
            @Param("tenantId") UUID tenantId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("afterSequenceNum") long afterSequenceNum,
            Pageable pageable
    );

    /**
     * Filtered by event type.
     * e.g. "show me all ride.cancelled events"
     */
    @Query("""
        SELECT e FROM AuditEventEntity e
        WHERE e.tenantId = :tenantId
          AND e.eventType = :eventType
          AND e.sequenceNum > :afterSequenceNum
        ORDER BY e.sequenceNum ASC
    """)
    List<AuditEventEntity> findPageByEventType(
            @Param("tenantId") UUID tenantId,
            @Param("eventType") String eventType,
            @Param("afterSequenceNum") long afterSequenceNum,
            Pageable pageable
    );

    /**
     * Time-range query — useful for the dashboard's date filter.
     * e.g. "show me everything that happened yesterday"
     */
    @Query("""
        SELECT e FROM AuditEventEntity e
        WHERE e.tenantId = :tenantId
          AND e.occurredAt >= :from
          AND e.occurredAt <= :to
          AND e.sequenceNum > :afterSequenceNum
        ORDER BY e.sequenceNum ASC
    """)
    List<AuditEventEntity> findPageByTimeRange(
            @Param("tenantId") UUID tenantId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("afterSequenceNum") long afterSequenceNum,
            Pageable pageable
    );
}

