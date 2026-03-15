package io.apptrace.server.dto.response;

import io.apptrace.server.domain.enums.ApiKeyScope;
import io.apptrace.server.domain.enums.ApiKeyStatus;
import io.apptrace.server.service.ApiKeyService;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Returned only on key creation.
 * Contains the rawKey — shown exactly once, never retrievable again.
 */
public record CreatedApiKeyResponse(
        UUID id,
        String keyPrefix,
        String description,
        ApiKeyStatus status,
        Set<ApiKeyScope> scopes,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        String rawKey   // ⚠️ shown once — save it immediately
) {
    public static CreatedApiKeyResponse from(ApiKeyService.CreatedApiKey created) {
        var k = created.apiKey();
        return new CreatedApiKeyResponse(
                k.getId(),
                k.getKeyPrefix(),
                k.getDescription(),
                k.getStatus(),
                k.getScopes(),
                k.getExpiresAt(),
                k.getCreatedAt(),
                created.rawKey());
    }
}
