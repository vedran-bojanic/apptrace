package io.apptrace.server.service;

import io.apptrace.server.domain.model.TenantEntity;
import io.apptrace.server.exception.ResourceNotFoundException;
import io.apptrace.server.exception.TenantAlreadyExistsException;
import io.apptrace.server.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantEntity create(String externalId, String displayName) {
        if (tenantRepository.existsByExternalId(externalId)) {
            throw new TenantAlreadyExistsException(externalId);
        }
        TenantEntity tenant = TenantEntity.builder()
                .externalId(externalId)
                .displayName(displayName)
                .build();
        return tenantRepository.save(tenant);
        // Note: the V3 DB trigger automatically creates the HashChain row
        // for this tenant right after the INSERT — no extra code needed here
    }

    @Transactional(readOnly = true)
    public TenantEntity getById(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));
    }

    @Transactional(readOnly = true)
    public TenantEntity getByExternalId(String externalId) {
        return tenantRepository.findByExternalId(externalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found for externalId: " + externalId));
    }

    public TenantEntity rename(UUID id, String newDisplayName) {
        TenantEntity tenant = getById(id);
        tenant.rename(newDisplayName);
        return tenantRepository.save(tenant);
    }

    public TenantEntity suspend(UUID id) {
        TenantEntity tenant = getById(id);
        tenant.suspend();
        return tenantRepository.save(tenant);
    }

    public TenantEntity activate(UUID id) {
        TenantEntity tenant = getById(id);
        tenant.activate();
        return tenantRepository.save(tenant);
    }
}

