package io.apptrace.server.domain.model;

import java.util.Base64;
import java.util.Objects;

/**
 * Opaque URL-safe cursor for keyset pagination.
 * <p/>
 * Encodes a sequence_num. Pagination queries use:
 *   WHERE tenant_id = :tenantId AND sequence_num > :cursor
 *   ORDER BY sequence_num ASC LIMIT :pageSize
 * <p/>
 * The long is Base64url-encoded so the API exposes an opaque string —
 * callers must never depend on its internal structure.
 */
public record Cursor(long sequenceNum) {

    public static final Cursor BEGINNING = new Cursor(0L);

    public Cursor {
        if (sequenceNum < 0)
            throw new IllegalArgumentException("sequenceNum must be >= 0");
    }

    public String encode() {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(Long.toString(sequenceNum).getBytes());
    }

    public static Cursor decode(String encoded) {
        Objects.requireNonNull(encoded, "cursor must not be null");
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            return new Cursor(Long.parseLong(new String(bytes)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + encoded, e);
        }
    }
}
