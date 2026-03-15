package io.apptrace.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank @Size(max = 256) String externalId,
        @NotBlank @Size(max = 256) String displayName
) {}

