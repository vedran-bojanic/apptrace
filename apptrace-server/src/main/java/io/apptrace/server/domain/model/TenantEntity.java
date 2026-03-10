package io.apptrace.server.domain.model;

import io.apptrace.server.domain.enums.TenantStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "tenants",
        uniqueConstraints = @UniqueConstraint(name = "uq_tenants_external_id", columnNames = "external_id")
)
public class TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private String externalId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TenantStatus status = TenantStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TenantEntity() {}

    private TenantEntity(Builder b) {
        this.externalId  = b.externalId;
        this.displayName = b.displayName;
        this.status      = b.status;
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    public void rename(String newDisplayName) {
        if (newDisplayName == null || newDisplayName.isBlank())
            throw new IllegalArgumentException("displayName must not be blank");
        this.displayName = newDisplayName;
    }

    public void suspend() {
        if (status == TenantStatus.DELETED)
            throw new IllegalStateException("Cannot suspend a deleted tenant");
        this.status = TenantStatus.SUSPENDED;
    }

    public void activate() {
        if (status == TenantStatus.DELETED)
            throw new IllegalStateException("Cannot activate a deleted tenant");
        this.status = TenantStatus.ACTIVE;
    }

    /** Soft-delete. Irreversible. */
    public void delete() {
        this.status = TenantStatus.DELETED;
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public UUID getId()                  { return id; }
    public String getExternalId()        { return externalId; }
    public String getDisplayName()       { return displayName; }
    public TenantStatus getStatus()      { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String externalId;
        private String displayName;
        private TenantStatus status = TenantStatus.ACTIVE;

        public Builder externalId(String v)   { this.externalId  = v; return this; }
        public Builder displayName(String v)  { this.displayName = v; return this; }
        public Builder status(TenantStatus v) { this.status      = v; return this; }

        public TenantEntity build() {
            if (externalId == null || externalId.isBlank())
                throw new IllegalArgumentException("externalId is required");
            if (displayName == null || displayName.isBlank())
                throw new IllegalArgumentException("displayName is required");
            return new TenantEntity(this);
        }
    }

    @Override
    public String toString() {
        return "Tenant{id=" + id + ", externalId='" + externalId + "', status=" + status + "}";
    }
}
