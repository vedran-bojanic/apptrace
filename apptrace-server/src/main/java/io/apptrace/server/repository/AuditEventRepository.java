package io.apptrace.server.repository;

import io.apptrace.server.domain.model.AuditEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    Optional<AuditEventEntity> findByTenantIdAndIdempotencyKey(
            UUID tenantId, String idempotencyKey);

    @Query("""
        SELECT e FROM AuditEventEntity e
        WHERE e.tenantId = :tenantId
          AND e.sequenceNum > :afterSequenceNum
        ORDER BY e.sequenceNum ASC
    """)
    List<AuditEventEntity> findPage(
            @Param("tenantId") UUID tenantId,
            @Param("afterSequenceNum") long afterSequenceNum,
            Pageable pageable);

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
            Pageable pageable);

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
            Pageable pageable);

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
            Pageable pageable);
}