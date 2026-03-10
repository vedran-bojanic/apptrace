-- =============================================================================
-- V2: Audit Events + Hash Chain
-- Append-only. No UPDATE or DELETE ever issued against audit_events.
-- =============================================================================

CREATE TABLE audit_events (
                              id               UUID        NOT NULL DEFAULT gen_random_uuid(),
                              tenant_id        UUID        NOT NULL,
                              sequence_num     BIGINT      NOT NULL,

    -- Actor
                              actor_type       TEXT        NOT NULL CHECK (char_length(actor_type) <= 64),
                              actor_id         TEXT        NOT NULL CHECK (char_length(actor_id) <= 256),
                              actor_name       TEXT,

    -- Action
                              event_type       TEXT        NOT NULL CHECK (char_length(event_type) <= 128),
                              event_category   TEXT        NOT NULL DEFAULT 'GENERAL',
                              severity         TEXT        NOT NULL DEFAULT 'INFO'
                                  CHECK (severity IN ('DEBUG','INFO','WARN','ERROR','CRITICAL')),

    -- Resource
                              resource_type    TEXT,
                              resource_id      TEXT,

    -- Payload
                              payload          JSONB       NOT NULL DEFAULT '{}',
                              metadata         JSONB       NOT NULL DEFAULT '{}',

    -- Integrity
                              payload_hash     TEXT        NOT NULL,
                              chain_hash       TEXT        NOT NULL,

    -- Timestamps
                              occurred_at      TIMESTAMPTZ NOT NULL,
                              received_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Source context
                              service_name     TEXT,
                              service_version  TEXT,
                              environment      TEXT,
                              trace_id         TEXT,
                              request_id       TEXT,

                              CONSTRAINT pk_audit_events        PRIMARY KEY (id),
                              CONSTRAINT uq_audit_events_seq    UNIQUE (tenant_id, sequence_num),
                              CONSTRAINT fk_audit_events_tenant FOREIGN KEY (tenant_id)
                                  REFERENCES tenants (id)
                                  ON DELETE RESTRICT
);

-- Immutability enforced at DB level
CREATE RULE no_update_audit_events AS ON UPDATE TO audit_events DO INSTEAD NOTHING;
CREATE RULE no_delete_audit_events AS ON DELETE TO audit_events DO INSTEAD NOTHING;

-- Indexes
CREATE INDEX idx_ae_tenant_occurred  ON audit_events (tenant_id, occurred_at DESC);
CREATE INDEX idx_ae_tenant_actor     ON audit_events (tenant_id, actor_id);
CREATE INDEX idx_ae_tenant_resource  ON audit_events (tenant_id, resource_type, resource_id)
    WHERE resource_type IS NOT NULL;
CREATE INDEX idx_ae_tenant_type      ON audit_events (tenant_id, event_type);
CREATE INDEX idx_ae_payload_gin      ON audit_events USING GIN (payload);

-- ---------------------------------------------------------------------------
-- HASH CHAIN HEAD — one row per tenant, updated on every append
-- ---------------------------------------------------------------------------
CREATE TABLE hash_chain (
                            tenant_id          UUID        NOT NULL,
                            last_sequence_num  BIGINT      NOT NULL DEFAULT 0,
                            last_chain_hash    TEXT        NOT NULL,
                            event_count        BIGINT      NOT NULL DEFAULT 0,
                            last_event_id      UUID,
                            version            BIGINT      NOT NULL DEFAULT 0,
                            updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

                            CONSTRAINT pk_hash_chain        PRIMARY KEY (tenant_id),
                            CONSTRAINT fk_hash_chain_tenant FOREIGN KEY (tenant_id)
                                REFERENCES tenants (id)
                                ON DELETE RESTRICT
);