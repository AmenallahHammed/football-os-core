-- Creates all schemas that fos-governance-service owns.
-- Each package (identity, canonical, policy, signal, audit) owns its schema exclusively.

CREATE SCHEMA IF NOT EXISTS fos_identity;
CREATE SCHEMA IF NOT EXISTS fos_canonical;
CREATE SCHEMA IF NOT EXISTS fos_policy;
CREATE SCHEMA IF NOT EXISTS fos_signal;
CREATE SCHEMA IF NOT EXISTS fos_audit;
