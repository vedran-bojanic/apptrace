-- =============================================================================
-- V1: Tenants and API Keys
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- TENANTS
-- ---------------------------------------------------------------------------
CREATE TABLE tenants (
                         id           UUID        NOT NULL DEFAULT gen_random_uuid(),
                         external_id  TEXT        NOT NULL,
                         display_name TEXT        NOT NULL,
                         status       TEXT        NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
                         created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                         updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

                         CONSTRAINT pk_tenants             PRIMARY KEY (id),
                         CONSTRAINT uq_tenants_external_id UNIQUE (external_id)
);

CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
RETURN NEW;
END;
$$;

CREATE TRIGGER trg_tenants_updated_at
    BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ---------------------------------------------------------------------------
-- API KEYS
-- Plaintext key is shown once at creation and never stored.
-- Only the SHA-256 hash is persisted.
-- ---------------------------------------------------------------------------
CREATE TABLE api_keys (
                          id          UUID        NOT NULL DEFAULT gen_random_uuid(),
                          tenant_id   UUID        NOT NULL,
                          key_hash    TEXT        NOT NULL,
                          key_prefix  VARCHAR(8)  NOT NULL,
                          description TEXT,
                          status      TEXT        NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('ACTIVE', 'REVOKED')),
                          scopes      VARCHAR(255) NOT NULL DEFAULT 'WRITE,READ',
                          expires_at  TIMESTAMPTZ,
                          last_used_at TIMESTAMPTZ,
                          created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                          revoked_at  TIMESTAMPTZ,

                          CONSTRAINT pk_api_keys          PRIMARY KEY (id),
                          CONSTRAINT uq_api_keys_key_hash UNIQUE (key_hash),
                          CONSTRAINT fk_api_keys_tenant   FOREIGN KEY (tenant_id)
                              REFERENCES tenants (id)
                              ON DELETE RESTRICT
);

CREATE INDEX idx_api_keys_tenant_id ON api_keys (tenant_id);
CREATE INDEX idx_api_keys_key_hash  ON api_keys (key_hash);