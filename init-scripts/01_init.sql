-- This script runs automatically when the Postgres container starts for the first time.
-- Docker mounts files from ./init-scripts/ into /docker-entrypoint-initdb.d/

-- The database and user are already created by the POSTGRES_* env vars in docker-compose.
-- This script handles any extra setup needed before Flyway runs.

-- Grant all privileges to the apptrace user (already the owner, but explicit is better)
GRANT ALL PRIVILEGES ON DATABASE apptrace TO apptrace;

-- Allow apptrace user to create extensions (needed for pgcrypto in V1 migration)
ALTER USER apptrace SUPERUSER;