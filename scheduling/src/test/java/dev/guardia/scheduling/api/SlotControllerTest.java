package dev.guardia.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.guardia.scheduling.ServiceTest;
import dev.guardia.scheduling.domain.Slot;
import dev.guardia.scheduling.domain.SlotHorizon;
import dev.guardia.scheduling.domain.SlotRepository;
import dev.guardia.scheduling.domain.SlotStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * The diary as the page reads it.
 *
 * <p>What matters is that the three states are distinguishable from outside, because the page
 * exists to show an hour going from held back to free. A view that reported "taken" for both
 * would hide exactly the thing worth watching.
 */
class SlotControllerTest extends ServiceTest {

	@Autowired
	WebApplicationContext context;

	@Autowired
	SlotRepository slots;

	@Autowired
	SlotHorizon horizon;

	@Autowired
	ObjectMapper objectMapper;

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		slots.deleteAll();
		horizon.topUp();
	}

	@Test
	@DisplayName("every state an hour can be in is visible from outside")
	void reportsFreeHeldAndBooked() throws Exception {
		UUID heldBy = UUID.randomUUID();
		UUID bookedBy = UUID.randomUUID();
		List<Slot> all = slots.findAll();

		Slot held = all.getFirst();
		held.holdFor(heldBy, Instant.now().plusSeconds(600));
		Slot booked = all.get(1);
		booked.holdFor(bookedBy, Instant.now().plusSeconds(600));
		booked.book();
		slots.saveAll(List.of(held, booked));

		List<SlotView> view = read();

		assertThat(view).extracting(SlotView::status).contains("FREE", "HELD", "BOOKED");
		assertThat(view).filteredOn(slot -> slot.id().equals(held.id()))
				.singleElement()
				.satisfies(slot -> {
					assertThat(slot.status()).isEqualTo("HELD");
					assertThat(slot.incidentId()).isEqualTo(heldBy);
					assertThat(slot.technicianName()).isNotBlank();
				});
		assertThat(view).filteredOn(slot -> slot.id().equals(booked.id()))
				.singleElement()
				.extracting(SlotView::status, SlotView::incidentId)
				.containsExactly("BOOKED", bookedBy);
	}

	@Test
	@DisplayName("a released hour reads as free again, with nobody's name on it")
	void releasedHoursLookUntaken() throws Exception {
		UUID incidentId = UUID.randomUUID();
		Slot detached = slots.findAll().getFirst();
		detached.holdFor(incidentId, Instant.now().plusSeconds(600));
		// Keep what save returns: it is a managed copy, and the one passed in keeps the
		// version it had before the write, which the next save would then be refused for.
		Slot slot = slots.saveAndFlush(detached);

		slot.release();
		slots.saveAndFlush(slot);

		assertThat(read()).filteredOn(view -> view.id().equals(slot.id()))
				.singleElement()
				.extracting(SlotView::status, SlotView::incidentId)
				.containsExactly("FREE", null);
	}

	@Test
	@DisplayName("hours already past are not offered to the page")
	void onlyUpcomingHours() throws Exception {
		assertThat(read())
				.isNotEmpty()
				.allSatisfy(view -> assertThat(view.startsAt()).isAfter(Instant.now()));
	}

	@Test
	@DisplayName("the page's origin may read it, and nothing else may")
	void corsIsNarrow() throws Exception {
		mvc.perform(get("/api/slots").header(HttpHeaders.ORIGIN, "http://localhost:8081"))
				.andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin"))
						.isEqualTo("http://localhost:8081"));

		mvc.perform(get("/api/slots").header(HttpHeaders.ORIGIN, "https://somewhere.example"))
				.andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin"))
						.as("an origin nobody allowed gets no permission back")
						.isNull());
	}

	private List<SlotView> read() throws Exception {
		String body = mvc.perform(get("/api/slots")).andReturn().getResponse().getContentAsString();
		return objectMapper.readValue(body, objectMapper.getTypeFactory()
				.constructCollectionType(List.class, SlotView.class));
	}
}
