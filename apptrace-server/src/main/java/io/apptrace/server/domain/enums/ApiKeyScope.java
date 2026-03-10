package io.apptrace.server.domain.enums;

public enum ApiKeyScope {
    /** Ingest new audit events. */
    WRITE,
    /** Query audit events. */
    READ,
    /** Administrative operations (tenant management, key rotation). */
    ADMIN
}
