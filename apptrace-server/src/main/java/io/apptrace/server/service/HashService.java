package io.apptrace.server.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Stateless SHA-256 hashing for the audit event chain.
 * <p/>
 * Canonical payload hash input (pipe-separated, keys sorted):
 *   {sequenceNum}|{tenantId}|{actorId}|{eventType}|{canonicalJson(payload)}
 * <p/>
 * This format is stable and mirrors fn_compute_payload_hash() in V3 migration SQL.
 * Any change here breaks all existing chain verification — treat as a protocol version.
 */
@Component
public class HashService {

    private static final String SEP = "|";

    /** SHA-256("GENESIS") — well-known seed for every new tenant's chain. */
    public static final String GENESIS_HASH = sha256("GENESIS");

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public String computePayloadHash(
            long sequenceNum,
            UUID tenantId,
            String actorId,
            String eventType,
            Map<String, Object> payload
    ) {
        String input = sequenceNum + SEP + tenantId + SEP
                + actorId + SEP + eventType + SEP + canonicalJson(payload);
        return sha256(input);
    }

    public String computeChainHash(String previousChainHash, String payloadHash) {
        return sha256(previousChainHash + payloadHash);
    }

    public boolean verifyPayloadHash(
            long sequenceNum, UUID tenantId,
            String actorId, String eventType,
            Map<String, Object> payload, String storedHash
    ) {
        return computePayloadHash(sequenceNum, tenantId, actorId, eventType, payload)
                .equals(storedHash);
    }

    public boolean verifyChainLink(
            String previousChainHash, String payloadHash, String storedChainHash
    ) {
        return computeChainHash(previousChainHash, payloadHash).equals(storedChainHash);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    md.digest(input.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Deterministic JSON — keys sorted lexicographically.
     * Must stay in sync with the SQL fn_compute_payload_hash() function.
     */
    @SuppressWarnings("unchecked")
    static String canonicalJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return "{}";
        var sorted = new TreeMap<>(payload);   // TreeMap = natural key order
        var sb = new StringBuilder("{");
        sorted.forEach((k, v) ->
                sb.append('"').append(escape(k)).append('"')
                        .append(':')
                        .append(jsonValue(v))
                        .append(',')
        );
        sb.deleteCharAt(sb.length() - 1);      // trailing comma
        return sb.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    private static String jsonValue(Object v) {
        if (v == null)              return "null";
        if (v instanceof String s)  return '"' + escape(s) + '"';
        if (v instanceof Number
                || v instanceof Boolean)   return v.toString();
        if (v instanceof Map m)     return canonicalJson(m);
        return '"' + escape(v.toString()) + '"';
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

