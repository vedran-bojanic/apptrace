package io.apptrace.server.dto.response;

import io.apptrace.server.domain.enums.ApiKeyScope;
import io.apptrace.server.domain.enums.ApiKeyStatus;
import io.apptrace.server.domain.model.ApiKeyEntity;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Safe API key representation — never includes the raw key or hash.
 * Used for listing and status checks.
 */
public record ApiKeyResponse(
        UUID id,
        String keyPrefix,
        String description,
        ApiKeyStatus status,
        Set<ApiKeyScope> scopes,
        OffsetDateTime expiresAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime createdAt
) {
    public static ApiKeyResponse from(ApiKeyEntity k) {
        return new ApiKeyResponse(
                k.getId(),
                k.getKeyPrefix(),
                k.getDescription(),
                k.getStatus(),
                k.getScopes(),
                k.getExpiresAt(),
                k.getLastUsedAt(),
                k.getCreatedAt());
    }
}
