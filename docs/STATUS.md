# Where this stands

Updated 2026-10-21.

## Done, and verified locally with `./mvnw verify`

The saga runs end to end over a real broker: an incident is raised, an hour of a technician's
day is held, the SLA is checked, and the appointment is either booked or the hour released.

- `platform` — transactional outbox, idempotent consumers, retry with widening back-off and a
  dead letter topic.
- `incidents` — REST API, the saga's state machine, server-sent events to the browser.
- `scheduling` — technicians, hours, provisional holds, compensation, hold expiry.
- `sla` — contracts and response times; the step that can say no.
- `e2e` — all three services in one JVM against a real Kafka and Postgres.
- A single static page served by `incidents` on 8081, with a button per outcome.
- `docker compose up --wait` builds all three services and starts the whole system, returning
  once each one reports healthy. All three endings were exercised against that stack by hand,
  not only in tests.

Every claim in the README has a test behind it, including the two that were until recently only
asserted in prose: a poison pill reaching the dead letter topic without stalling the partition
behind it, and one publisher stepping over an outbox row another is holding rather than
queueing behind it.

30 tests in the last full run: 11 in `incidents`, 9 in `scheduling`, 5 in `sla`, 5 in `e2e`.

## Next

1. Rescheduling an incident whose first attempt was cancelled. Today a cancellation is the end
   of that saga, and a human has to raise another incident. Doing it properly means deciding
   what a second attempt is allowed to do — try a later hour, a different technician, or
   neither — and that is a design question rather than a coding one.
2. Observability: the useful version is not a tracing library but an answer to "where did this
   incident get to", which means correlating one incident's events across three services.
3. Nothing here is authenticated. That is fine for what this repository is for, and it is the
   first thing to say out loud if asked.

## Things learned the hard way, so they are not learned twice

- Spring Boot 4 brings Jackson 3: `tools.jackson.databind.ObjectMapper`, unchecked exceptions,
  annotations still under `com.fasterxml.jackson.annotation`.
- `@EntityScan` now lives in `org.springframework.boot.persistence.autoconfigure`.
- Testcontainers 2 renamed its artifacts (`testcontainers-postgresql`) and its packages
  (`org.testcontainers.postgresql`). `apache/kafka:3.9.0` would not start under it;
  `ConfluentKafkaContainer` with `confluentinc/cp-kafka:7.8.0` does.
- Boot's own `KafkaTemplate` is typed `<?, ?>` and will not satisfy an injection point asking
  for `<String, String>`; this project declares its own.
- `SpringApplicationBuilder.properties()` lands in default properties and loses to any
  `application.yml` on the classpath, which matters when three services share one. The
  end-to-end harness passes configuration as command line arguments instead.
- A `@Scheduled` fixed delay already fires once at startup. Pairing it with an
  `ApplicationReadyEvent` listener ran the agenda top-up twice at once.
- Keep the container singleton: `@Testcontainers` stops a static container after each test
  class, and the next class then finds a dead port.
- `apache/kafka:3.9.0` refuses the two-listener arrangement this project needs, failing at
  storage format time with a complaint about `0.0.0.0` in the advertised listeners, and it
  would not start under Testcontainers either. `confluentinc/cp-kafka:7.8.0` takes it, and is
  now what both the tests and `compose.yaml` use.
- Port 5432 on the host belongs to whatever database is already running there, which on this
  machine is another project's. Guardia publishes 55432 instead, and the services' default
  connection string points at that, so starting a service from source cannot quietly talk to
  the wrong database.
