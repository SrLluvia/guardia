# Guardia

Incident dispatch for a support desk. An incident arrives with a priority, a technician's hour
is held for it, the customer's contracted response time is checked, and the appointment is
either confirmed or given back. Three services, no shared database, and no transaction that
spans them.

> The saga runs end to end — holding, confirming, compensating and timing out — and every
> claim below is held up by a test that forces the failure rather than describing it.

This file is about how it is built and why. For what it does — the rules it applies, the
technicians and contracts it is seeded with, and a list of things to try in the page with what
each one should do — see [docs/FUNCTIONAL.md](docs/FUNCTIONAL.md).

## The problem worth solving

The business rule is three sentences long. The hard part is that the three steps live in three
services, so there is no transaction to wrap around them. Two things can go wrong at once:

1. A step succeeds and the next one fails, leaving a technician's hour held for an appointment
   that will never happen.
2. A step succeeds and the announcement is lost, or arrives twice, so the other services
   believe something that is not true — or act on the same fact twice.

Guardia is built around those two problems rather than around the feature.

## How the saga runs

```
POST /api/incidents
        │
        ▼
┌────────────────┐   incident-events    ┌─────────────────┐
│   incidents    │─────────────────────▶│   scheduling    │
│                │                      │                 │
│ owns the       │                      │ owns technicians│
│ outcome        │◀─────────────────────│ and their hours │
└────────────────┘  scheduling-events   └─────────────────┘
        ▲                                        │
        │          sla-events           ┌────────▼────────┐
        └───────────────────────────────│       sla       │
                                        │ owns contracts  │
                                        └─────────────────┘
```

1. `incidents` records the incident and announces it.
2. `scheduling` finds the earliest free hour of a technician with the right skill and **holds**
   it — provisionally, with a deadline — then says which hour it took, or that it had none.
3. `sla` compares that hour against what the customer's contract promises for that priority,
   and approves or rejects.
4. `incidents` draws the conclusion: assigned, or cancelled with a reason.
5. `scheduling` hears the conclusion and either books the hour or **releases it**. That release
   is the compensation.

One service decides. The other two report facts about what they own. No central coordinator
has to be running for a saga to finish.

## The parts that make it more than a demo

**The event and the state change commit together.** Writing to the database and publishing to
Kafka are two systems, and doing them one after another means a crash in between leaves them
disagreeing. Instead the event is written into an `outbox` table inside the same transaction as
the change it describes, and a poller publishes it afterwards. Publication can therefore happen
twice, but never zero times — which is the right way round, because a duplicate is cheap to
absorb and a lost event is not.

**Consumers are idempotent, because Kafka is at-least-once.** Every event carries a message id.
A consumer records that id and does its work in one transaction, so a repeat delivery finds the
id already there and does nothing. This is not decoration: without it a retried delivery would
hold a second hour, or book one twice.

**Every saga ends.** A hold carries an expiry. If the service that owes the next answer never
sends it, a sweep releases the hour and announces the expiry, and the incident reaches a
terminal state instead of waiting for a reply that is not coming. A verdict that arrives after
that is recognised as late and ignored rather than applied.

**A poison message cannot stop the queue.** A record that fails every time is retried with a
widening gap and then copied to `<topic>.DLT`, so the partition behind it keeps moving.

**Ordering is arranged, not hoped for.** Kafka only promises order within a partition, so every
event about one incident is keyed by the incident id and lands on one partition, in order.
Across incidents there is no ordering, and none is needed.

**Services share infrastructure, not contracts.** The outbox and the idempotency guard live in
a shared `platform` module, because that code would otherwise be identical three times. Event
classes deliberately do not: each service declares the fields it reads and ignores the rest, so
one service's release cannot dictate another's. Payloads travel as plain JSON text and are
deserialised explicitly, with no type headers naming a class the consumer must own.

## The tests provoke the failures

The interesting tests do not assert that the happy path works. They break things:

| What is forced | What must hold |
|---|---|
| The same message delivered twice | The work happens once; one confirmation, not two |
| The SLA rejects after an hour was held | The incident is cancelled **and** the hour is free again, and the next incident can have it |
| The reply never comes | The hold expires, the hour is released, the incident ends |
| A verdict arrives after a timeout | It is ignored; a finished saga does not move |
| Two incidents want the same skill | They get different hours |
| An incident nobody has heard of | The event is dropped and nothing is written |
| A record that can never be read | It reaches `<topic>.DLT`, and an incident raised afterwards still completes |
| One publisher holding an outbox row | A second steps over it rather than queueing behind it |

The end-to-end suite starts all three services in one JVM against a real Kafka and a real
Postgres, posts an incident through the public API, and waits for the three of them to talk to
each other. It reaches into no service to move one along.

| Module | What it is |
|---|---|
| `platform` | Outbox, idempotency, dead-letter handling. No domain, no contracts. |
| `incidents` | The public API and the saga's state machine. Owns the outcome. |
| `scheduling` | Technicians, their hours, holds. Owns the scarce resource. |
| `sla` | Contracts and response times. The step that can say no. |
| `e2e` | All three together, over a real broker. |

## Running it

Requires Docker. One command builds the three services and starts them with Postgres and
Kafka, and returns once they report healthy rather than merely started:

```bash
docker compose up --wait
```

Then open <http://localhost:8081>, which has a button for each ending.
[docs/FUNCTIONAL.md](docs/FUNCTIONAL.md) lists what to click and what should happen, down to
the incident that is cancelled and the one that then takes the hour it was holding.

For working on it, run the infrastructure in Docker and the services from source instead —
Kafka advertises a second address for exactly this, so a service on the host and a service in
the network reach the same broker:

```bash
docker compose up -d postgres kafka
```

```bash
./mvnw -pl incidents spring-boot:run
```

`incidents` serves the API on 8081, `scheduling` on 8082 and `sla` on 8083. Raise an incident —
`acme` is promised 24 hours at `NORMAL` priority, so this one gets assigned:

```bash
curl -X POST localhost:8081/api/incidents -H "content-type: application/json" -d "{\"customerId\":\"acme\",\"priority\":\"NORMAL\",\"requiredSkill\":\"NETWORK\",\"summary\":\"Branch office offline\"}"
```

The reply is `202 Accepted` with status `RECEIVED`: a saga that has started, not an appointment
that exists. Read it back a moment later and it will be `ASSIGNED`:

```bash
curl localhost:8081/api/incidents
```

For the other ending, raise one for `globex` at `CRITICAL`, which is promised 15 minutes.
Nothing in the diary can meet that, so the incident is cancelled and the hour handed straight
back.

### Tests

```bash
./mvnw verify
```

Testcontainers starts Postgres and Kafka itself, so Docker must be running. The database is
real because what is under test — one hour per incident, the outbox and the state change
committing together, a released hour becoming available again — is database behaviour, and a
stand-in would only prove that the stand-in agrees with itself.

## Decisions worth defending

**One Postgres instance, one schema and one role per service.** No role can reach another
service's schema, so the boundary is enforced by the database rather than by everyone
remembering. In production these would be separate instances; here they are not, because three
containers would buy nothing a reader can see.

**A hold is not a booking.** Making the provisional state explicit is what allows a timeout to
exist at all. If scheduling simply booked the hour, there would be nothing to expire.

**Rejection is an answer, not a failure.** An uncovered customer, or an hour outside the
contract, produces a rejection event rather than an exception. Throwing would retry, then
dead-letter, and leave the saga hanging until its hold expired: a slower route to a worse
version of the same outcome.

**The publication queue is a table, not another broker.** The outbox poller claims rows with
`SELECT ... FOR UPDATE SKIP LOCKED`, which gives at-most-one-publisher-per-row without adding
anything else to operate.

**The diary is filled at startup, not by a migration.** Timestamps written into a migration are
wrong the next day, and an agenda whose hours are all in the past demonstrates nothing.

## Deliberately not here

Authentication and tenancy, Kubernetes manifests, distributed tracing, per-consumer retry
policies, an admin interface, technician skills as anything richer than a string, and
rescheduling a cancelled incident. Each is a day of work that would add nothing to the question
this repository is about.
