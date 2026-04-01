-- Flyway-managed schema for JWT auth users.
-- Hibernate ddl-auto is still enabled in dev; IF NOT EXISTS keeps startup safe.

CREATE TABLE IF NOT EXISTS app_users (
    username VARCHAR(120) PRIMARY KEY,
    password_hash VARCHAR(200) NOT NULL,
    role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL
);

