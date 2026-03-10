package io.apptrace.server.domain.model;

import io.apptrace.server.domain.enums.ApiKeyScope;
import io.apptrace.server.domain.enums.ApiKeyStatus;
import io.apptrace.server.domain.model.converters.ApiKeyScopesConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_api_keys_tenant"))
    private TenantEntity tenant;

    @Column(name = "key_hash", nullable = false, updatable = false, unique = true, length = 64)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, updatable = false, length = 8)
    private String keyPrefix;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ApiKeyStatus status = ApiKeyStatus.ACTIVE;

    @Convert(converter = ApiKeyScopesConverter.class)
    @Column(name = "scopes", nullable = false)
    private Set<ApiKeyScope> scopes = EnumSet.of(ApiKeyScope.WRITE, ApiKeyScope.READ);

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected ApiKeyEntity() {}

    private ApiKeyEntity(Builder b) {
        this.tenant      = b.tenant;
        this.keyHash     = b.keyHash;
        this.keyPrefix   = b.keyPrefix;
        this.description = b.description;
        this.scopes      = EnumSet.copyOf(b.scopes);  // Set -> EnumSet, no array involved
        this.expiresAt   = b.expiresAt;
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    public void revoke() {
        if (status == ApiKeyStatus.REVOKED)
            throw new IllegalStateException("Key is already revoked");
        this.status    = ApiKeyStatus.REVOKED;
        this.revokedAt = OffsetDateTime.now();
    }

    public void recordUsage() {
        this.lastUsedAt = OffsetDateTime.now();
    }

    public boolean isActive() {
        if (status != ApiKeyStatus.ACTIVE) return false;
        return expiresAt == null || OffsetDateTime.now().isBefore(expiresAt);
    }

    public boolean hasScope(ApiKeyScope scope) {
        return scopes.contains(scope);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public UUID getId()                   { return id; }
    public TenantEntity getTenant()       { return tenant; }
    public String getKeyHash()            { return keyHash; }
    public String getKeyPrefix()          { return keyPrefix; }
    public String getDescription()        { return description; }
    public ApiKeyStatus getStatus()       { return status; }
    public Set<ApiKeyScope> getScopes()   { return EnumSet.copyOf(scopes); }  // scopes is already a Set, just copy it
    public OffsetDateTime getExpiresAt()  { return expiresAt; }
    public OffsetDateTime getLastUsedAt() { return lastUsedAt; }
    public OffsetDateTime getCreatedAt()  { return createdAt; }
    public OffsetDateTime getRevokedAt()  { return revokedAt; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private TenantEntity tenant;
        private String keyHash;
        private String keyPrefix;
        private String description;
        private Set<ApiKeyScope> scopes = EnumSet.of(ApiKeyScope.WRITE, ApiKeyScope.READ);
        private OffsetDateTime expiresAt;

        public Builder tenant(TenantEntity v)     { this.tenant      = v; return this; }
        public Builder keyHash(String v)          { this.keyHash     = v; return this; }
        public Builder keyPrefix(String v)        { this.keyPrefix   = v; return this; }
        public Builder description(String v)      { this.description = v; return this; }
        public Builder scopes(Set<ApiKeyScope> v) { this.scopes      = v; return this; }
        public Builder expiresAt(OffsetDateTime v){ this.expiresAt   = v; return this; }

        public ApiKeyEntity build() {
            if (tenant    == null)                        throw new IllegalArgumentException("tenant is required");
            if (keyHash   == null || keyHash.isBlank())   throw new IllegalArgumentException("keyHash is required");
            if (keyPrefix == null || keyPrefix.isBlank()) throw new IllegalArgumentException("keyPrefix is required");
            if (scopes    == null || scopes.isEmpty())    throw new IllegalArgumentException("at least one scope is required");
            return new ApiKeyEntity(this);
        }
    }
}
