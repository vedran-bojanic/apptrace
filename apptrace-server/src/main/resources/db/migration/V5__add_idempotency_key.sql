-- =============================================================================
-- V5: Add idempotency_key to audit_events
--
-- Idempotency key is a client-supplied UUID that prevents duplicate events.
-- If the same key is submitted twice, the second request returns the original
-- event instead of creating a new one.
--
-- Scoped per tenant — two different tenants can use the same idempotency key.
-- =============================================================================

ALTER TABLE audit_events
    ADD COLUMN idempotency_key VARCHAR(255);

CREATE UNIQUE INDEX uq_audit_events_idempotency
    ON audit_events (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;