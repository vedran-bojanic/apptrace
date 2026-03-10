-- =============================================================================
-- V3: Helper functions and triggers
-- =============================================================================

CREATE OR REPLACE FUNCTION fn_genesis_hash()
RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
SELECT encode(digest('GENESIS', 'sha256'), 'hex');
$$;

-- Auto-seed hash_chain when a tenant is created
CREATE OR REPLACE FUNCTION fn_init_hash_chain()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
INSERT INTO hash_chain (tenant_id, last_sequence_num, last_chain_hash, event_count)
VALUES (NEW.id, 0, fn_genesis_hash(), 0)
    ON CONFLICT (tenant_id) DO NOTHING;
RETURN NEW;
END;
$$;

CREATE TRIGGER trg_tenant_init_chain
    AFTER INSERT ON tenants
    FOR EACH ROW EXECUTE FUNCTION fn_init_hash_chain();

-- Chain integrity verification — returns tampered rows
-- Usage: SELECT * FROM fn_verify_chain('<tenant-uuid>');
CREATE OR REPLACE FUNCTION fn_verify_chain(p_tenant_id UUID)
RETURNS TABLE (
    sequence_num        BIGINT,
    event_id            UUID,
    stored_chain_hash   TEXT,
    expected_chain_hash TEXT,
    is_valid            BOOLEAN
)
LANGUAGE plpgsql AS $$
BEGIN
RETURN QUERY
    WITH ordered AS (
        SELECT ae.sequence_num, ae.id AS event_id, ae.payload_hash, ae.chain_hash
        FROM audit_events ae
        WHERE ae.tenant_id = p_tenant_id
        ORDER BY ae.sequence_num
    ),
    chained AS (
        SELECT
            o.sequence_num,
            o.event_id,
            o.chain_hash AS stored_chain_hash,
            encode(
                digest(
                    LAG(o.chain_hash, 1, fn_genesis_hash()) OVER (ORDER BY o.sequence_num)
                    || o.payload_hash,
                    'sha256'
                ),
                'hex'
            ) AS expected_chain_hash
        FROM ordered o
    )
SELECT
    c.sequence_num,
    c.event_id,
    c.stored_chain_hash,
    c.expected_chain_hash,
    c.stored_chain_hash = c.expected_chain_hash AS is_valid
FROM chained c;
END;
$$;