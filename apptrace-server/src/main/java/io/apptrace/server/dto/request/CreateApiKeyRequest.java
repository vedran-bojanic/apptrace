package io.apptrace.server.dto.request;

import io.apptrace.server.domain.enums.ApiKeyScope;
import jakarta.validation.constraints.NotEmpty;

import java.time.OffsetDateTime;
import java.util.Set;

public record CreateApiKeyRequest(
        String description,
        @NotEmpty Set<ApiKeyScope> scopes,
        OffsetDateTime expiresAt   // null = never expires
) {}
