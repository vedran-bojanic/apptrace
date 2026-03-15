package io.apptrace.server.integration;

import io.apptrace.server.domain.enums.ApiKeyScope;
import io.apptrace.server.domain.model.TenantEntity;
import io.apptrace.server.repository.AuditEventRepository;
import io.apptrace.server.service.ApiKeyService;
import io.apptrace.server.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IngestionIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TenantService tenantService;
    @Autowired ApiKeyService apiKeyService;
    @Autowired AuditEventRepository eventRepository;

    private String rawApiKey;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        // Create a fresh tenant and API key for each test
        String externalId = "test-tenant-" + UUID.randomUUID();
        TenantEntity tenant = tenantService.create(externalId, "Test Tenant");
        tenantId = tenant.getId();

        ApiKeyService.CreatedApiKey created = apiKeyService.create(
                tenantId, "test key",
                Set.of(ApiKeyScope.WRITE, ApiKeyScope.READ),
                null);
        rawApiKey = created.rawKey();
    }

    // -------------------------------------------------------------------------
    // Single event ingestion
    // -------------------------------------------------------------------------

    @Test
    void ingest_validEvent_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "actor": { "id": "user-123", "type": "user" },
                      "action": "document.deleted",
                      "resource": { "type": "document", "id": "doc-456" },
                      "metadata": { "reason": "user request" },
                      "occurredAt": "2026-03-08T10:00:00Z"
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.sequenceNum").value(1))
                .andExpect(jsonPath("$.actor.id").value("user-123"))
                .andExpect(jsonPath("$.action").value("document.deleted"))
                .andExpect(jsonPath("$.resource.type").value("document"));
    }

    @Test
    void ingest_missingActor_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "action": "document.deleted"
                    }
                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingest_missingAction_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "actor": { "id": "user-123", "type": "user" }
                    }
                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingest_noApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "actor": { "id": "user-123", "type": "user" },
                      "action": "document.deleted"
                    }
                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingest_invalidApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer invalid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "actor": { "id": "user-123", "type": "user" },
                      "action": "document.deleted"
                    }
                """))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    @Test
    void ingest_sameIdempotencyKey_doesNotCreateDuplicate() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = """
            {
              "idempotencyKey": "%s",
              "actor": { "id": "user-123", "type": "user" },
              "action": "document.deleted",
              "occurredAt": "2026-03-08T10:00:00Z"
            }
        """.formatted(idempotencyKey);

        // First request — creates the event
        String firstResponse = mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Second request with same key — returns existing event
        String secondResponse = mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();

        // Same event ID returned, only one event in DB
        assertThat(firstResponse).contains(idempotencyKey);
        assertThat(secondResponse).contains(idempotencyKey);
        assertThat(eventRepository.findAll().stream()
                .filter(e -> idempotencyKey.equals(e.getIdempotencyKey()))
                .count()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Batch ingestion
    // -------------------------------------------------------------------------

    @Test
    void ingestBatch_validEvents_returnsAllCreated() throws Exception {
        mockMvc.perform(post("/api/v1/events/batch")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "events": [
                        {
                          "actor": { "id": "user-1", "type": "user" },
                          "action": "order.created",
                          "occurredAt": "2026-03-08T10:00:00Z"
                        },
                        {
                          "actor": { "id": "user-2", "type": "user" },
                          "action": "order.cancelled",
                          "occurredAt": "2026-03-08T10:01:00Z"
                        }
                      ]
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accepted").value(2))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events.length()").value(2));
    }

    @Test
    void ingestBatch_emptyList_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/events/batch")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    { "events": [] }
                """))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Hash chain integrity
    // -------------------------------------------------------------------------

    @Test
    void ingest_multipleEvents_sequenceNumsAreIncremental() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/events")
                            .header("Authorization", "Bearer " + rawApiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                        {
                          "actor": { "id": "user-123", "type": "user" },
                          "action": "action.%d",
                          "occurredAt": "2026-03-08T10:00:00Z"
                        }
                    """.formatted(i)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sequenceNum").value(i));
        }
    }
}