package io.apptrace.server.dto.response;

import io.apptrace.server.domain.enums.TenantStatus;
import io.apptrace.server.domain.model.TenantEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String externalId,
        String displayName,
        TenantStatus status,
        OffsetDateTime createdAt
) {
    public static TenantResponse from(TenantEntity t) {
        return new TenantResponse(
                t.getId(),
                t.getExternalId(),
                t.getDisplayName(),
                t.getStatus(),
                t.getCreatedAt());
    }
}
