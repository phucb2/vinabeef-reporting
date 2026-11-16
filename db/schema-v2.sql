-- =====================================================================
-- VinaBeef Livestock Farm Management System — shared database, schema v2
-- PostgreSQL 15+. One cluster, one schema per subsystem (write owner).
--
-- Conventions (apply everywhere)
--   * snake_case, singular table names, always schema-qualified
--   * PK = <entity>_id; surrogate keys are uuid, natural codes are UNIQUE
--   * append-only logs use bigint identity keys
--   * every instant is timestamptz (stored UTC, shown in Asia/Ho_Chi_Minh)
--   * enums are CHECK constraints listing EVERY value (no "...")
--   * FK only inside a write-owner boundary; a reference to another
--     subsystem's table is a plain column marked "ref ->" (read-only join)
--   * no physical delete and no is_deleted flag: rows change status
--   * mutable master rows carry version (optimistic locking) + timestamps
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;          -- gen_random_uuid()

CREATE SCHEMA IF NOT EXISTS barn_gate;
CREATE SCHEMA IF NOT EXISTS cattle_tag;
CREATE SCHEMA IF NOT EXISTS movement;
CREATE SCHEMA IF NOT EXISTS health;
CREATE SCHEMA IF NOT EXISTS reporting;
CREATE SCHEMA IF NOT EXISTS security;

-- ---------------------------------------------------------------------
-- barn_gate  (owner: Cattle, Tag, Barn Gate & Reader Station subsystem)
-- ---------------------------------------------------------------------
CREATE TABLE barn_gate.farm (
    farm_id     varchar(8)   PRIMARY KEY,                 -- DLK, GLI, DNI
    name        varchar(100) NOT NULL,
    province    varchar(60)  NOT NULL,
    status      varchar(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CLOSED'))
);

CREATE TABLE barn_gate.barn (
    barn_id     varchar(16)  PRIMARY KEY,                 -- GLI-B07
    farm_id     varchar(8)   NOT NULL REFERENCES barn_gate.farm,
    capacity    int          NOT NULL CHECK (capacity > 0),
    status      varchar(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CLOSED')),
    version     int          NOT NULL DEFAULT 0
);

CREATE TABLE barn_gate.gate (
    gate_id     varchar(20)  PRIMARY KEY,                 -- GLI-B07-G1
    barn_id     varchar(16)  NOT NULL REFERENCES barn_gate.barn,
    gate_type   varchar(10)  NOT NULL CHECK (gate_type IN ('ENTRY','EXIT','BIDIR')),
    leads_to    varchar(10)  NOT NULL CHECK (leads_to IN ('PASTURE','YARD','BARN')),
    status      varchar(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CLOSED'))
);

CREATE TABLE barn_gate.reader_station (
    reader_id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    gate_id            varchar(20)  NOT NULL REFERENCES barn_gate.gate,
    serial_no          varchar(40)  NOT NULL UNIQUE,
    model              varchar(40)  NOT NULL,
    status             varchar(15)  NOT NULL CHECK (status IN ('COMMISSIONING','ONLINE','OFFLINE','RETIRED')),
    active_config_ver  int,                                -- -> reader_config.config_version
    last_heartbeat_at  timestamptz
);

-- Versioned reader configuration (AD-5 / P15) — described in the text, missing from Figure 5.2
CREATE TABLE barn_gate.reader_config (
    reader_id       uuid         NOT NULL REFERENCES barn_gate.reader_station,
    config_version  int          NOT NULL,
    settings        jsonb        NOT NULL,                 -- antennas, power, IN/OUT mapping
    test_read_ok    boolean      NOT NULL DEFAULT false,   -- Online only after a passed test read
    created_by      uuid         NOT NULL,                 -- ref -> security.app_user
    created_at      timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (reader_id, config_version)
);

-- ---------------------------------------------------------------------
-- cattle_tag  (owner: Cattle, Tag, Barn Gate & Reader Station subsystem)
-- ---------------------------------------------------------------------
CREATE TABLE cattle_tag.cattle (
    animal_id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    expected_barn_id  varchar(16)  NOT NULL REFERENCES barn_gate.barn,  -- same write owner
    breed             varchar(40)  NOT NULL,
    sex               varchar(6)   NOT NULL CHECK (sex IN ('MALE','FEMALE')),
    birth_date        date,
    origin            varchar(10)  NOT NULL CHECK (origin IN ('BORN','PURCHASED')),
    status            varchar(10)  NOT NULL CHECK (status IN ('ACTIVE','SOLD','DEAD','CULLED')),
    version           int          NOT NULL DEFAULT 0,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_cattle_barn_status ON cattle_tag.cattle (expected_barn_id, status);

CREATE TABLE cattle_tag.rfid_tag (
    rfid_tag  char(15)     PRIMARY KEY CHECK (rfid_tag ~ '^[0-9]{15}$'),  -- ISO 11784, 15 digits
    batch_no  varchar(30)  NOT NULL,
    status    varchar(10)  NOT NULL CHECK (status IN ('IN_STOCK','ACTIVE','RETIRED','LOST'))
);

CREATE TABLE cattle_tag.tag_assignment (
    assignment_id  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    animal_id      uuid         NOT NULL REFERENCES cattle_tag.cattle,
    rfid_tag       char(15)     NOT NULL REFERENCES cattle_tag.rfid_tag,
    assigned_at    timestamptz  NOT NULL DEFAULT now(),
    unassigned_at  timestamptz,                            -- null = active
    reason         varchar(10)  CHECK (reason IN ('LOST','DAMAGED','REPLACED','EXIT')),
    CHECK (unassigned_at IS NULL OR unassigned_at > assigned_at),
    CHECK ((unassigned_at IS NULL) = (reason IS NULL))
);
-- P6 "one active tag per animal"
CREATE UNIQUE INDEX ux_tag_active_animal ON cattle_tag.tag_assignment (animal_id) WHERE unassigned_at IS NULL;
-- P6 "no re-used tag number": a tag appears in one assignment, ever (also covers "one animal per tag")
CREATE UNIQUE INDEX ux_tag_never_reused  ON cattle_tag.tag_assignment (rfid_tag);

-- Transactional outbox (AD-8) — described in Chapter 6.1, missing from the figures.
-- Same shape in every schema that publishes events.
CREATE TABLE cattle_tag.outbox_event (
    event_id        uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  varchar(30)  NOT NULL,                -- CATTLE, TAG
    aggregate_id    varchar(40)  NOT NULL,
    event_type      varchar(40)  NOT NULL,                -- cattle.registered, tag.assigned
    event_version   int          NOT NULL DEFAULT 1,
    payload         jsonb        NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    published_at    timestamptz                           -- null = not yet relayed
);
CREATE INDEX ix_outbox_unpublished ON cattle_tag.outbox_event (created_at) WHERE published_at IS NULL;

-- ---------------------------------------------------------------------
-- movement  (owner: Movement Counting & Alerting subsystem)
-- ~50,000 rows/day ≈ 3.65 GB/year → partitioned by month on occurred_at
-- ---------------------------------------------------------------------
CREATE TABLE movement.herd_session (
    session_id  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    barn_id     varchar(16)  NOT NULL,                     -- ref -> barn_gate.barn
    status      varchar(10)  NOT NULL CHECK (status IN ('RELEASING','RETURNING','CLOSED')),
    opened_at   timestamptz  NOT NULL,
    closed_at   timestamptz,
    out_count   int          NOT NULL DEFAULT 0,
    in_count    int          NOT NULL DEFAULT 0
);

CREATE TABLE movement.movement (
    event_id        uuid         NOT NULL,                 -- generated at the gate (idempotency key)
    occurred_at     timestamptz  NOT NULL,                 -- scan time at the gate
    received_at     timestamptz  NOT NULL DEFAULT now(),   -- arrival at the centre (store-and-forward lag)
    rfid_tag        char(15),                              -- null for MANUAL entries without a read
    animal_id       uuid,                                  -- ref -> cattle_tag.cattle; null when UNKNOWN
    gate_id         varchar(20)  NOT NULL,                 -- ref -> barn_gate.gate
    barn_id         varchar(16)  NOT NULL,                 -- copied from the gate at ingest: immutable fact
    session_id      uuid         REFERENCES movement.herd_session,
    direction       varchar(3)   NOT NULL CHECK (direction IN ('IN','OUT')),
    classification  varchar(10)  NOT NULL CHECK (classification IN ('KNOWN','UNKNOWN','WRONG_BARN','DUPLICATE')),
    source          varchar(6)   NOT NULL CHECK (source IN ('RFID','MANUAL')),
    PRIMARY KEY (event_id, occurred_at)                    -- partition key must be in the PK
) PARTITION BY RANGE (occurred_at);
CREATE TABLE movement.movement_2026_11 PARTITION OF movement.movement
    FOR VALUES FROM ('2026-11-01 00:00+07') TO ('2026-12-01 00:00+07');
CREATE INDEX ix_movement_barn_time   ON movement.movement (barn_id, occurred_at);
CREATE INDEX ix_movement_animal_time ON movement.movement (animal_id, occurred_at);

CREATE TABLE movement.alert (
    alert_id     uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id   uuid         REFERENCES movement.herd_session,
    barn_id      varchar(16)  NOT NULL,                    -- ref -> barn_gate.barn
    animal_id    uuid,                                     -- ref -> cattle_tag.cattle (when known)
    alert_type   varchar(20)  NOT NULL CHECK (alert_type IN ('MISSING','UNKNOWN_TAG','WRONG_BARN','READER_OFFLINE')),
    severity     varchar(10)  NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    status       varchar(10)  NOT NULL CHECK (status IN ('OPEN','ACK','RESOLVED')),
    raised_at    timestamptz  NOT NULL DEFAULT now(),
    resolved_at  timestamptz,
    resolved_by  uuid,                                     -- ref -> security.app_user
    CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);

CREATE TABLE movement.transfer (
    transfer_id   uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    animal_id     uuid         NOT NULL,                   -- ref -> cattle_tag.cattle
    from_barn_id  varchar(16)  NOT NULL,                   -- ref -> barn_gate.barn
    to_barn_id    varchar(16)  NOT NULL,                   -- ref -> barn_gate.barn
    planned_date  date         NOT NULL,
    status        varchar(10)  NOT NULL CHECK (status IN ('PLANNED','DONE','CANCELLED')),
    requested_by  uuid         NOT NULL,                   -- ref -> security.app_user
    completed_at  timestamptz,
    CHECK (from_barn_id <> to_barn_id)
);

-- ---------------------------------------------------------------------
-- health  (owner: Vaccination, Health & Movement History subsystem)
-- One table for examinations, treatments and vaccinations. A correction is
-- a NEW row pointing at the old one; the old row's status becomes CORRECTED.
-- ---------------------------------------------------------------------
CREATE TABLE health.health_record (
    record_id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    animal_id           uuid         NOT NULL,             -- ref -> cattle_tag.cattle
    barn_id             varchar(16)  NOT NULL,             -- barn at the time of the event (history-safe)
    kind                varchar(12)  NOT NULL CHECK (kind IN ('EXAMINATION','TREATMENT','VACCINATION')),
    diagnosis           varchar(200),                      -- required for EXAMINATION / TREATMENT
    disease_code        varchar(20),                       -- national disease list code (NADCS)
    outbreak_suspected  boolean      NOT NULL DEFAULT false,
    vaccine_or_drug     varchar(80),
    lot_no              varchar(30),
    dose                varchar(30),
    notes               text,
    performed_at        timestamptz  NOT NULL,
    performed_by        uuid         NOT NULL,             -- ref -> security.app_user (vet)
    corrects_record_id  uuid         REFERENCES health.health_record,
    status              varchar(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CORRECTED','VOIDED')),
    created_at          timestamptz  NOT NULL DEFAULT now(),
    CHECK (kind = 'VACCINATION' OR diagnosis IS NOT NULL),
    CHECK (kind = 'EXAMINATION' OR vaccine_or_drug IS NOT NULL),
    CHECK (NOT outbreak_suspected OR disease_code IS NOT NULL)
);
CREATE INDEX ix_health_animal_time ON health.health_record (animal_id, performed_at);
CREATE INDEX ix_health_kind_time   ON health.health_record (kind, performed_at) WHERE status = 'ACTIVE';
CREATE INDEX ix_health_outbreak    ON health.health_record (performed_at) WHERE outbreak_suspected AND status = 'ACTIVE';

-- ---------------------------------------------------------------------
-- reporting  (owner: Reporting subsystem)
-- NADCS submissions are batches: one daily submission carries many cases.
-- ---------------------------------------------------------------------
CREATE TABLE reporting.nadcs_submission (
    submission_id  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    sink_id        varchar(20)  NOT NULL DEFAULT 'NADCS',
    period_from    date         NOT NULL,
    period_to      date         NOT NULL,
    status         varchar(10)  NOT NULL CHECK (status IN ('PENDING','SENT','ACKED','REJECTED','FAILED')),
    attempts       int          NOT NULL DEFAULT 0,
    receipt_id     varchar(60),                            -- returned by NADCS
    payload        jsonb        NOT NULL,                  -- exactly what was sent
    created_at     timestamptz  NOT NULL DEFAULT now(),
    sent_at        timestamptz,
    UNIQUE (sink_id, period_from, period_to)               -- a period is never sent twice
);

CREATE TABLE reporting.nadcs_submission_item (
    submission_id  uuid  NOT NULL REFERENCES reporting.nadcs_submission,
    record_id      uuid  NOT NULL,                         -- ref -> health.health_record
    PRIMARY KEY (submission_id, record_id)
);

-- ---------------------------------------------------------------------
-- security  (owner: System Administration & Security subsystem)
-- Keycloak authenticates; this schema is the source of truth for
-- role + farm/barn scope, which Keycloak copies into the JWT claims.
-- ---------------------------------------------------------------------
CREATE TABLE security.app_user (
    user_id        uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    idp_subject    varchar(64)   NOT NULL UNIQUE,          -- Keycloak "sub"
    username       varchar(60)   NOT NULL UNIQUE,
    full_name      varchar(100)  NOT NULL,
    email          varchar(120)  NOT NULL UNIQUE,
    phone          varchar(20),
    status         varchar(10)   NOT NULL CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
    last_login_at  timestamptz
);

-- One list of role codes for the whole system (matches the JWT roles used in Chapter 6)
CREATE TABLE security.role (
    role_id      varchar(20)   PRIMARY KEY CHECK (role_id IN ('ADMIN','MANAGER','SUPERVISOR','VET','EXEC','AUDITOR')),
    name         varchar(60)   NOT NULL,
    description  varchar(200)
);

CREATE TABLE security.permission (
    permission_id  varchar(40)   PRIMARY KEY,              -- cattle.register, report.export
    resource       varchar(20)   NOT NULL,
    action         varchar(10)   NOT NULL CHECK (action IN ('VIEW','CREATE','UPDATE','APPROVE','EXPORT')),
    description    varchar(200)
);

CREATE TABLE security.role_permission (
    role_id        varchar(20)  NOT NULL REFERENCES security.role,
    permission_id  varchar(40)  NOT NULL REFERENCES security.permission,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE security.user_role (                          -- called ROLE_SCOPE in 6.5: use one name
    user_role_id  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid         NOT NULL REFERENCES security.app_user,
    role_id       varchar(20)  NOT NULL REFERENCES security.role,
    farm_id       varchar(8),                              -- ref -> barn_gate.farm; null = all farms
    barn_id       varchar(16),                             -- ref -> barn_gate.barn; null = whole farm
    valid_from    date         NOT NULL DEFAULT current_date,
    valid_to      date,                                    -- null = no end
    granted_by    uuid         NOT NULL REFERENCES security.app_user,
    CHECK (barn_id IS NULL OR farm_id IS NOT NULL),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE TABLE security.approval_request (
    request_id        uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type       varchar(30)   NOT NULL,              -- CATTLE
    entity_id         varchar(40)   NOT NULL,
    requested_change  jsonb         NOT NULL,              -- {"status": "SOLD"}
    reason            varchar(200)  NOT NULL,
    requested_by      uuid          NOT NULL REFERENCES security.app_user,
    decided_by        uuid          REFERENCES security.app_user,
    status            varchar(10)   NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    decision_note     varchar(200),
    created_at        timestamptz   NOT NULL DEFAULT now(),
    decided_at        timestamptz,
    CHECK (decided_by IS NULL OR decided_by <> requested_by)   -- four-eyes rule
);

-- Business audit trail: one shape for Figure 5.4, Section 6.4 and Section 6.5.
-- No FK on actor: the log must accept any row and outlive user changes.
CREATE TABLE security.audit_log (
    audit_id        bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at     timestamptz   NOT NULL DEFAULT now(),
    actor_user_id   uuid,                                  -- null for system / device actions
    actor_type      varchar(10)   NOT NULL CHECK (actor_type IN ('USER','SYSTEM','DEVICE')),
    action          varchar(10)   NOT NULL CHECK (action IN ('CREATE','UPDATE','STATUS','APPROVE','EXPORT','SYNC')),
    entity_type     varchar(30)   NOT NULL,                -- CATTLE, GATE, HEALTH_RECORD…
    entity_id       varchar(40)   NOT NULL,
    before_value    jsonb,
    after_value     jsonb,
    source          varchar(10)   NOT NULL CHECK (source IN ('WEB','MOBILE','GATE','API','JOB')),
    client_ip       inet,
    correlation_id  varchar(64)   NOT NULL
);
CREATE INDEX ix_audit_entity ON security.audit_log (entity_type, entity_id, occurred_at);
CREATE INDEX ix_audit_actor  ON security.audit_log (actor_user_id, occurred_at);

-- Security events kept apart from business audit (P11)
CREATE TABLE security.security_event (
    event_id        bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at     timestamptz   NOT NULL DEFAULT now(),
    user_id         uuid,                                  -- null when the username is unknown
    username_tried  varchar(60),
    event_type      varchar(20)   NOT NULL CHECK (event_type IN ('LOGIN_OK','LOGIN_FAILED','LOCKED','ACCESS_DENIED','ROLE_CHANGED','SETTING_CHANGED')),
    client_ip       inet,
    details         jsonb,
    correlation_id  varchar(64)
);

-- ---------------------------------------------------------------------
-- Grants: one writer per schema, read-only cross-schema (P8, P12)
-- ---------------------------------------------------------------------
-- Reporting reads the replica with a read-only role and writes only its own schema.
-- CREATE ROLE reporting_ro;  GRANT USAGE ON SCHEMA cattle_tag, movement, health TO reporting_ro;
-- GRANT SELECT ON ALL TABLES IN SCHEMA cattle_tag, movement, health TO reporting_ro;
-- CREATE ROLE reporting_rw;  GRANT USAGE, CREATE ON SCHEMA reporting TO reporting_rw;
-- Audit log is append-only:
-- GRANT INSERT, SELECT ON security.audit_log, security.security_event TO security_app;
-- REVOKE UPDATE, DELETE, TRUNCATE ON security.audit_log, security.security_event FROM PUBLIC, security_app;
