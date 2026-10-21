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

Every claim in the README has a test behind it, including the two that were until recently only
asserted in prose: a poison pill reaching the dead letter topic without stalling the partition
behind it, and one publisher stepping over an outbox row another is holding rather than
queueing behind it.

30 tests in the last full run: 11 in `incidents`, 9 in `scheduling`, 5 in `sla`, 5 in `e2e`.

## Next

1. Merge the pull request — but only after the history below has been pushed, or the trailer
   ends up in `main` for good.
2. The history has been rewritten locally to drop the `Co-Authored-By` trailer from the first
   two commits. It needs two force pushes, which the permission mode in this setup blocks:

   ```
   git push --force origin feat/saga-foundation
   git push --force origin $(git rev-list --max-parents=0 feat/saga-foundation):refs/heads/main
   ```

3. Worth having: Dockerfiles so each service is deployable on its own, an `/actuator/health`
   check in `compose.yaml`, and rescheduling an incident whose first attempt was cancelled.

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
