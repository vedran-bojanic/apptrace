package io.apptrace.server.repository;

import io.apptrace.server.domain.model.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    Optional<TenantEntity> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);
}