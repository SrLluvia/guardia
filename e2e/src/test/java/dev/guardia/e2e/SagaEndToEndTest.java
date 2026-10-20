package dev.guardia.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.guardia.scheduling.domain.Slot;
import dev.guardia.scheduling.domain.SlotRepository;
import dev.guardia.scheduling.domain.SlotStatus;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * The saga, through the front door and over a real broker.
 *
 * <p>Nothing here reaches into a service to move it along: an incident is posted to the public
 * API and the test then waits for the three services to talk to each other. What it asserts is
 * the pair of things a caller actually cares about — the incident reached the right conclusion,
 * and the technician's hour ended up in the right state.
 *
 * <p>The diary is deliberately six hours out, so the contracted response time decides the
 * outcome rather than whatever time the build runs at.
 */
class SagaEndToEndTest {

	private static RestClient client;

	@BeforeAll
	static void startTheSystem() {
		GuardiaCluster.start();
		client = RestClient.create(GuardiaCluster.incidentsBaseUrl());
	}

	@Test
	@DisplayName("a generous contract ends with the incident assigned and the hour booked")
	void happyPathAcrossThreeServices() {
		// acme is promised 24 hours at NORMAL priority; the earliest slot is six hours out.
		String incidentId = open("acme", "NORMAL", "NETWORK", "Branch office offline");

		await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
				assertThat(status(incidentId)).isEqualTo("ASSIGNED"));

		// The incident reaching ASSIGNED only means its own service has concluded. Scheduling
		// learns of that conclusion over Kafka, so the booking is awaited rather than assumed:
		// asserting it immediately would be testing this JVM's timing, not the system.
		await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
				assertThat(slotFor(incidentId).status())
						.as("the hour must end up booked, not merely held")
						.isEqualTo(SlotStatus.BOOKED));
		assertThat(detail(incidentId)).contains("within the contracted response time");
	}

	@Test
	@DisplayName("a response time that cannot be met cancels the incident and gives the hour back")
	void compensationAcrossThreeServices() {
		// globex is promised 15 minutes at CRITICAL. Nothing in the diary can meet that.
		String incidentId = open("globex", "CRITICAL", "NETWORK", "Core switch down");

		await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
				assertThat(status(incidentId)).isEqualTo("CANCELLED"));

		assertThat(detail(incidentId)).contains("response time");
		// The compensation is the point: scheduling held an hour, and has let it go again.
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
				assertThat(slots().findByIncidentId(UUID.fromString(incidentId))).isEmpty());
	}

	@Test
	@DisplayName("a skill nobody has is cancelled without anything being held")
	void noTechnicianAvailable() {
		String incidentId = open("acme", "NORMAL", "QUANTUM", "Needs a skill nobody has");

		await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
				assertThat(status(incidentId)).isEqualTo("CANCELLED"));

		assertThat(detail(incidentId)).contains("QUANTUM");
		assertThat(slots().findByIncidentId(UUID.fromString(incidentId))).isEmpty();
	}

	@Test
	@DisplayName("two incidents competing for the same skill get different hours")
	void concurrentIncidentsDoNotShareAnHour() {
		String first = open("acme", "NORMAL", "DATABASE", "Replication lag");
		String second = open("acme", "NORMAL", "DATABASE", "Slow queries");

		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(status(first)).isEqualTo("ASSIGNED");
			assertThat(status(second)).isEqualTo("ASSIGNED");
		});
		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			assertThat(slotFor(first).status()).isEqualTo(SlotStatus.BOOKED);
			assertThat(slotFor(second).status()).isEqualTo(SlotStatus.BOOKED);
		});

		Slot a = slotFor(first);
		Slot b = slotFor(second);
		assertThat(a.id()).as("two incidents must not be handed the same hour").isNotEqualTo(b.id());
		assertThat(a.startsAt()).isNotEqualTo(b.startsAt());
	}

	private String open(String customerId, String priority, String skill, String summary) {
		Map<?, ?> created = client.post()
				.uri("/api/incidents")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of(
						"customerId", customerId,
						"priority", priority,
						"requiredSkill", skill,
						"summary", summary))
				.retrieve()
				.body(Map.class);

		assertThat(created).isNotNull();
		// Accepted, not created: what exists so far is a saga, not an assignment.
		assertThat(created.get("status")).isEqualTo("RECEIVED");
		return (String) created.get("id");
	}

	private String status(String incidentId) {
		return (String) view(incidentId).get("status");
	}

	private String detail(String incidentId) {
		return (String) view(incidentId).get("detail");
	}

	private Map<?, ?> view(String incidentId) {
		return client.get().uri("/api/incidents/{id}", incidentId).retrieve().body(Map.class);
	}

	private SlotRepository slots() {
		return GuardiaCluster.bean("scheduling", SlotRepository.class);
	}

	private Slot slotFor(String incidentId) {
		return slots().findByIncidentId(UUID.fromString(incidentId)).orElseThrow();
	}
}
