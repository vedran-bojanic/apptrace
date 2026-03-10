-- =============================================================================
-- V4: Replace api_key_scopes join table with a native Postgres TEXT[] array
--
-- The @ElementCollection approach requires a separate join table.
-- Storing scopes as TEXT[] on api_keys directly is simpler and idiomatic
-- for Postgres — no join needed, Hibernate maps it via hypersistence-utils.
-- =============================================================================

-- The scopes column already exists on api_keys as TEXT[] from V1.
-- Hibernate will map it using @Array from hypersistence-utils.
-- Nothing to change in the DB — this migration documents the decision.

-- If you previously created an api_key_scopes table, drop it:
DROP TABLE IF EXISTS api_key_scopes;