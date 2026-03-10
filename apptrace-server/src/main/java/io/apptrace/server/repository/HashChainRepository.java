package io.apptrace.server.repository;

import io.apptrace.server.domain.model.HashChainEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HashChainRepository extends JpaRepository<HashChainEntity, UUID> {

    /**
     * Loads the chain head with a PESSIMISTIC WRITE lock.
     *
     * Why pessimistic here and not just optimistic (@Version)?
     *
     * We use BOTH. The @Version on HashChain handles the common case of
     * two events arriving slightly apart — optimistic is fast and enough.
     *
     * But for high-throughput tenants sending many events per second,
     * optimistic retries create a thundering herd. This locked version
     * is used by the service when it detects it's in a high-contention
     * scenario, or during chain repair operations.
     *
     * For normal ingestion, use findById() — the @Version handles it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM HashChainEntity h WHERE h.tenantId = :tenantId")
    Optional<HashChainEntity> findByTenantIdWithLock(@Param("tenantId") UUID tenantId);
}

