-- Read-only login the app uses (mirrors the read-replica setup described at the end of schema-v2.sql).
CREATE ROLE reporting_ro LOGIN PASSWORD 'reporting_ro';
GRANT CONNECT ON DATABASE vinabeef TO reporting_ro;
GRANT USAGE  ON SCHEMA barn_gate, cattle_tag, movement, health, reporting TO reporting_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA barn_gate, cattle_tag, movement, health, reporting TO reporting_ro;
