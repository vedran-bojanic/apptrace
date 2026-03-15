package io.apptrace.server.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * HTTP request body for bulk event ingestion.
 * Max 100 events per batch to prevent oversized requests.
 */
public record BatchIngestRequest(

        @NotEmpty
        @Size(max = 100, message = "Batch size cannot exceed 100 events")
        @Valid
        List<IngestRequest> events
) {}