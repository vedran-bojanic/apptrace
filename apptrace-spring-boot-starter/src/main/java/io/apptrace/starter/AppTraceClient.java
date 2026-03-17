package io.apptrace.starter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * HTTP client for sending audit events to an AppTrace server.
 *
 * Obtain via Spring injection — AppTraceAutoConfiguration creates this bean
 * automatically when apptrace.server-url and apptrace.api-key are set.
 *
 * Usage:
 *
 *   // Simple
 *   appTraceClient.record(b -> b
 *       .actor("user-123", "user")
 *       .action("order.created")
 *       .resource("order", "order-789")
 *   );
 *
 *   // Full
 *   appTraceClient.record(b -> b
 *       .actor("user-123", "user", "Jane Doe")
 *       .action("document.deleted")
 *       .resource("document", "doc-456")
 *       .metadata(Map.of("reason", "user request"))
 *       .occurredAt(OffsetDateTime.now())
 *   );
 *
 *   // Batch
 *   appTraceClient.recordBatch(List.of(
 *       b -> b.actor("user-1", "user").action("order.created"),
 *       b -> b.actor("user-2", "user").action("order.cancelled")
 *   ));
 */
public class AppTraceClient {

    private static final Logger log = LoggerFactory.getLogger(AppTraceClient.class);

    private static final String EVENTS_PATH       = "/api/v1/events";
    private static final String EVENTS_BATCH_PATH = "/api/v1/events/batch";

    private final RestClient    restClient;
    private final boolean       enabled;

    AppTraceClient(RestClient restClient, boolean enabled) {
        this.restClient = restClient;
        this.enabled    = enabled;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Records a single audit event.
     *
     * @param builder lambda that configures the event
     */
    public void record(Consumer<EventBuilder> builder) {
        if (!enabled) {
            log.debug("AppTrace is disabled — skipping event");
            return;
        }
        EventBuilder b = new EventBuilder();
        builder.accept(b);
        sendSingle(b.build());
    }

    /**
     * Records multiple audit events in a single HTTP request.
     * More efficient than calling record() in a loop.
     *
     * @param builders list of lambdas, one per event
     */
    public void recordBatch(List<Consumer<EventBuilder>> builders) {
        if (!enabled) {
            log.debug("AppTrace is disabled — skipping batch of {} events", builders.size());
            return;
        }
        if (builders.isEmpty()) return;

        List<Map<String, Object>> events = builders.stream()
                .map(b -> {
                    EventBuilder eb = new EventBuilder();
                    b.accept(eb);
                    return eb.build();
                })
                .toList();

        sendBatch(events);
    }

    // -------------------------------------------------------------------------
    // HTTP calls
    // -------------------------------------------------------------------------

    private void sendSingle(Map<String, Object> event) {
        try {
            restClient.post()
                    .uri(EVENTS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to send audit event to AppTrace: {}", e.getMessage(), e);
            // We intentionally swallow the exception — audit logging must never
            // break the calling application's main flow
        }
    }

    private void sendBatch(List<Map<String, Object>> events) {
        try {
            restClient.post()
                    .uri(EVENTS_BATCH_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("events", events))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to send batch of {} audit events to AppTrace: {}",
                    events.size(), e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Fluent builder for a single audit event.
     * Mirrors the IngestRequest shape on the server.
     */
    public static final class EventBuilder {

        private String idempotencyKey;
        private String actorId;
        private String actorType;
        private String actorName;
        private String action;
        private String resourceType;
        private String resourceId;
        private String category;
        private String severity;
        private Map<String, Object> metadata;
        private OffsetDateTime occurredAt;
        private String serviceName;
        private String serviceVersion;
        private String environment;
        private String traceId;
        private String requestId;

        /** Sets actor ID and type. e.g. actor("user-123", "user") */
        public EventBuilder actor(String id, String type) {
            this.actorId   = id;
            this.actorType = type;
            return this;
        }

        /** Sets actor ID, type, and display name. */
        public EventBuilder actor(String id, String type, String name) {
            this.actorId   = id;
            this.actorType = type;
            this.actorName = name;
            return this;
        }

        /** The action that was performed. e.g. "order.created", "document.deleted" */
        public EventBuilder action(String action) {
            this.action = action;
            return this;
        }

        /** The resource the action was performed on. */
        public EventBuilder resource(String type, String id) {
            this.resourceType = type;
            this.resourceId   = id;
            return this;
        }

        public EventBuilder category(String category)           { this.category       = category;   return this; }
        public EventBuilder severity(String severity)           { this.severity       = severity;   return this; }
        public EventBuilder metadata(Map<String, Object> meta)  { this.metadata       = meta;       return this; }
        public EventBuilder occurredAt(OffsetDateTime t)        { this.occurredAt     = t;          return this; }
        public EventBuilder idempotencyKey(String key)          { this.idempotencyKey = key;        return this; }
        public EventBuilder serviceName(String v)               { this.serviceName    = v;          return this; }
        public EventBuilder serviceVersion(String v)            { this.serviceVersion = v;          return this; }
        public EventBuilder environment(String v)               { this.environment    = v;          return this; }
        public EventBuilder traceId(String v)                   { this.traceId        = v;          return this; }
        public EventBuilder requestId(String v)                 { this.requestId      = v;          return this; }

        Map<String, Object> build() {
            if (actorId   == null) throw new IllegalArgumentException("actor id is required");
            if (actorType == null) throw new IllegalArgumentException("actor type is required");
            if (action    == null) throw new IllegalArgumentException("action is required");

            // Build actor object
            var actor = new java.util.LinkedHashMap<String, Object>();
            actor.put("id",   actorId);
            actor.put("type", actorType);
            if (actorName != null) actor.put("name", actorName);

            // Build event map matching IngestRequest JSON shape
            var event = new java.util.LinkedHashMap<String, Object>();
            if (idempotencyKey != null) event.put("idempotencyKey", idempotencyKey);
            event.put("actor",  actor);
            event.put("action", action);

            if (resourceType != null) {
                event.put("resource", Map.of("type", resourceType, "id", resourceId));
            }
            if (category      != null) event.put("category",       category);
            if (severity      != null) event.put("severity",       severity);
            if (metadata      != null) event.put("metadata",       metadata);
            if (occurredAt    != null) event.put("occurredAt",     occurredAt.toString());
            if (serviceName   != null) event.put("serviceName",    serviceName);
            if (serviceVersion!= null) event.put("serviceVersion", serviceVersion);
            if (environment   != null) event.put("environment",    environment);
            if (traceId       != null) event.put("traceId",        traceId);
            if (requestId     != null) event.put("requestId",      requestId);

            return event;
        }
    }
}

