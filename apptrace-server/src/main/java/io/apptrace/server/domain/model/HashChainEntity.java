package io.apptrace.server.domain.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tracks the SHA-256 chain head per tenant.
 * </p>
 * One row per tenant. Appending a new event requires an optimistic-lock
 * update on this single row rather than scanning audit_events for the
 * latest hash.
 * <p/>
 * Concurrency: uses @Version for optimistic locking. The service layer
 * catches OptimisticLockingFailureException and retries the full
 * sequence-allocation + insert under a fresh transaction.
 */
@Entity
@Table(name = "hash_chain")
public class HashChainEntity {

    @Id
    @Column(name = "tenant_id", updatable = false, nullable = false)
    private UUID tenantId;

    @Column(name = "last_sequence_num", nullable = false)
    private long lastSequenceNum = 0L;

    /** SHA-256 hex of the most recent event's chain_hash. */
    @Column(name = "last_chain_hash", nullable = false, length = 64)
    private String lastChainHash;

    @Column(name = "event_count", nullable = false)
    private long eventCount = 0L;

    @Column(name = "last_event_id")
    private UUID lastEventId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected HashChainEntity() {}

    public HashChainEntity(UUID tenantId, String genesisHash) {
        this.tenantId      = tenantId;
        this.lastChainHash = genesisHash;
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /**
     * Advances the chain head after a new event has been persisted.
     *
     * @param newSequenceNum must be exactly lastSequenceNum + 1
     * @param newChainHash   the chain_hash of the newly persisted event
     * @param eventId        the UUID of the newly persisted event
     */
    public void advance(long newSequenceNum, String newChainHash, UUID eventId) {
        if (newSequenceNum != this.lastSequenceNum + 1)
            throw new IllegalArgumentException(
                    "Sequence gap: expected %d but got %d"
                            .formatted(this.lastSequenceNum + 1, newSequenceNum));
        this.lastSequenceNum = newSequenceNum;
        this.lastChainHash   = newChainHash;
        this.lastEventId     = eventId;
        this.eventCount++;
        this.updatedAt       = OffsetDateTime.now();
    }

    /** The next sequence number to assign to a new event. */
    public long nextSequenceNum() {
        return lastSequenceNum + 1;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public UUID getTenantId()           { return tenantId; }
    public long getLastSequenceNum()    { return lastSequenceNum; }
    public String getLastChainHash()    { return lastChainHash; }
    public long getEventCount()         { return eventCount; }
    public UUID getLastEventId()        { return lastEventId; }
    public long getVersion()            { return version; }
    public OffsetDateTime getUpdatedAt(){ return updatedAt; }
}

