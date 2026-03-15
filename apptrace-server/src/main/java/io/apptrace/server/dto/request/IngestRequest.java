package io.apptrace.server.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.apptrace.server.domain.enums.EventSeverity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * HTTP request body for ingesting a single audit event.
 *
 * The tenant is NOT part of this request — it is resolved from the API key
 * in the auth filter and injected by the controller.
 */
public record IngestRequest(

        @Size(max = 255)
        String idempotencyKey,

        @NotNull @Valid
        Actor actor,

        @NotBlank @Size(max = 128)
        String action,

        @Valid
        Resource resource,

        @Size(max = 64)
        String category,

        EventSeverity severity,

        Map<String, Object> metadata,

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime occurredAt,

        // Source context — optional, enriched server-side if missing
        String serviceName,
        String serviceVersion,
        String environment,
        String traceId,
        String requestId
) {

    /**
     * Who performed the action.
     *
     * @param id   required — your system's user/service ID
     * @param type required — e.g. "user", "service", "system"
     * @param name optional — display name
     * @param ip   optional — enriched from request if missing
     */
    public record Actor(
            @NotBlank @Size(max = 256) String id,
            @NotBlank @Size(max = 64)  String type,
            @Size(max = 256)           String name,
            String ip
    ) {}

    /**
     * What the action was performed on.
     *
     * @param type required — e.g. "document", "order", "user"
     * @param id   required — your system's resource ID
     */
    public record Resource(
            @NotBlank @Size(max = 128) String type,
            @NotBlank @Size(max = 256) String id
    ) {}
}
