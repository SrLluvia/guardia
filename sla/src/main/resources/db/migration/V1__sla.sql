-- What each customer's contract promises, per priority: how long until someone turns up.
CREATE TABLE contract (
    customer_id      text    NOT NULL,
    priority         text    NOT NULL,
    response_minutes integer NOT NULL CHECK (response_minutes > 0),
    PRIMARY KEY (customer_id, priority)
);

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
