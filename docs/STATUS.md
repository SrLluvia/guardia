# Where this stands

Updated 2026-10-20.

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

28 tests passed in the last full run: 10 in `incidents`, 9 in `scheduling`, 5 in `sla`, 4 in
`e2e`.

## In flight

`DeadLetterEndToEndTest` was written but has not been run locally. It publishes an unreadable
record to `incident-events` and asserts two things: the record reaches `incident-events.DLT`,
and an incident raised afterwards still completes — the claim that one poison pill cannot stop
a partition. CI on the pull request is what will confirm it.

## Next

1. Confirm the dead letter test passes, then merge the pull request.
2. The first two commits carry a `Co-Authored-By` trailer that should not be there. Removing it
   needs a history rewrite and a force push; one of those commits is already on `main`.
3. Worth having, in rough order of value: a test that two publishers cannot send the same
   outbox row twice (`FOR UPDATE SKIP LOCKED` is claimed but not demonstrated), Dockerfiles so
   each service is deployable on its own, and an `/actuator/health` check in `compose.yaml`.

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
