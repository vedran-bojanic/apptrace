package io.apptrace.server.controller;

import io.apptrace.server.domain.model.TenantEntity;
import io.apptrace.server.dto.request.CreateTenantRequest;
import io.apptrace.server.dto.response.TenantResponse;
import io.apptrace.server.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(@Valid @RequestBody CreateTenantRequest request) {
        TenantEntity tenant = tenantService.create(
                request.externalId(),
                request.displayName());
        return TenantResponse.from(tenant);
    }

    @GetMapping("/{id}")
    public TenantResponse getById(@PathVariable UUID id) {
        return TenantResponse.from(tenantService.getById(id));
    }
}