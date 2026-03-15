package io.apptrace.server.controller;

import io.apptrace.server.dto.request.CreateApiKeyRequest;
import io.apptrace.server.dto.response.ApiKeyResponse;
import io.apptrace.server.dto.response.CreatedApiKeyResponse;
import io.apptrace.server.service.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * Creates a new API key.
     * The rawKey in the response is shown ONCE — never stored, never retrievable again.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedApiKeyResponse create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        ApiKeyService.CreatedApiKey created = apiKeyService.create(
                tenantId,
                request.description(),
                request.scopes(),
                request.expiresAt());
        return CreatedApiKeyResponse.from(created);
    }

    @GetMapping
    public List<ApiKeyResponse> list(@PathVariable UUID tenantId) {
        return apiKeyService.listForTenant(tenantId)
                .stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    @DeleteMapping("/{apiKeyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @PathVariable UUID tenantId,
            @PathVariable UUID apiKeyId
    ) {
        apiKeyService.revoke(apiKeyId);
    }
}