-- An incident is the saga: its status is the saga's state, and it is the only place
-- the outcome is decided. The other services report facts; this one draws conclusions.
CREATE TABLE incident (
    id                  uuid        PRIMARY KEY,
    customer_id         text        NOT NULL,
    priority            text        NOT NULL,
    required_skill      text        NOT NULL,
    summary             text        NOT NULL,
    status              text        NOT NULL,
    technician_id       uuid,
    slot_id             uuid,
    scheduled_start     timestamptz,
    resolution_detail   text,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    version             bigint      NOT NULL
);

CREATE INDEX idx_incident_status ON incident (status, created_at DESC);

-- Shared infrastructure tables (see module `platform`). Each service owns its own copy:
-- the outbox of one service is none of another's business.
CREATE TABLE outbox (
    id           uuid        PRIMARY KEY,
    aggregate_id uuid        NOT NULL,
    topic        text        NOT NULL,
    event_type   text        NOT NULL,
    payload      text        NOT NULL,
    created_at   timestamptz NOT NULL,
    published_at timestamptz
);

-- Partial index: the publisher only ever asks for the unpublished tail, which stays small
-- even once the table holds every event the service has ever emitted.
CREATE INDEX idx_outbox_pending ON outbox (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_message (
    message_id   uuid        PRIMARY KEY,
    processed_at timestamptz NOT NULL
);
