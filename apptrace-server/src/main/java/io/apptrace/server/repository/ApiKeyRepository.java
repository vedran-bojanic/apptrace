package io.apptrace.server.repository;

import io.apptrace.server.domain.enums.ApiKeyStatus;
import io.apptrace.server.domain.model.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {

    /**
     * Auth hot path — called on every incoming request.
     * Fetches the key together with its tenant in one query to avoid N+1.
     */
    @Query("""
        SELECT k FROM ApiKeyEntity k
        JOIN FETCH k.tenant t
        WHERE k.keyHash = :keyHash
          AND k.status = 'ACTIVE'
    """)
    Optional<ApiKeyEntity> findActiveByKeyHash(@Param("keyHash") String keyHash);

    List<ApiKeyEntity> findAllByTenantId(UUID tenantId);

    List<ApiKeyEntity> findAllByTenantIdAndStatus(UUID tenantId, ApiKeyStatus status);
}
