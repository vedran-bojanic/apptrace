package io.apptrace.server.dto.response;

import java.util.List;

/**
 * HTTP response body for a batch ingest.
 * Returns each event that was created (or the existing one if idempotency key matched).
 */
public record BatchIngestResponse(
        int accepted,
        int duplicates,
        List<AuditEventResponse> events
) {
    public static BatchIngestResponse of(List<AuditEventResponse> events, int duplicates) {
        return new BatchIngestResponse(events.size(), duplicates, events);
    }
}