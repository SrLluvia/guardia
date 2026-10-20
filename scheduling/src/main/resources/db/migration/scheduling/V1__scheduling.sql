-- The scarce resource. A slot is free, held for one incident, or booked to it.
CREATE TABLE technician (
    id    uuid PRIMARY KEY,
    name  text NOT NULL,
    skill text NOT NULL
);

CREATE TABLE slot (
    id              uuid        PRIMARY KEY,
    technician_id   uuid        NOT NULL REFERENCES technician (id),
    starts_at       timestamptz NOT NULL,
    ends_at         timestamptz NOT NULL,
    status          text        NOT NULL,
    incident_id     uuid,
    hold_expires_at timestamptz,
    version         bigint      NOT NULL,
    CONSTRAINT slot_ends_after_start CHECK (ends_at > starts_at),
    -- A held or booked slot must say whose it is; a free one must not.
    CONSTRAINT slot_holder_matches_status CHECK (
        (status = 'FREE'   AND incident_id IS NULL AND hold_expires_at IS NULL)
     OR (status = 'HELD'   AND incident_id IS NOT NULL AND hold_expires_at IS NOT NULL)
     OR (status = 'BOOKED' AND incident_id IS NOT NULL)
    )
);

-- A technician has one of each hour, whatever a top-up run believes.
CREATE UNIQUE INDEX slot_one_per_technician_hour ON slot (technician_id, starts_at);

-- One slot per incident at most, enforced by the database rather than by hoping.
CREATE UNIQUE INDEX idx_slot_one_per_incident ON slot (incident_id) WHERE incident_id IS NOT NULL;
CREATE INDEX idx_slot_free_by_time ON slot (starts_at) WHERE status = 'FREE';
CREATE INDEX idx_slot_expiring ON slot (hold_expires_at) WHERE status = 'HELD';

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
