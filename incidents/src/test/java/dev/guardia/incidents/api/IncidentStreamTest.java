package dev.guardia.incidents.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.guardia.incidents.ServiceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The stream has to say hello.
 *
 * <p>Spring writes nothing to an {@code SseEmitter} response until the first event, so a page
 * that opens the stream and then sits there sees no headers, never fires its {@code open}
 * handler, and shows "connecting" indefinitely — while actually being connected. The fix is a
 * comment sent at subscription time, and this is the test that notices if it goes away.
 */
class IncidentStreamTest extends ServiceTest {

	@Autowired
	WebApplicationContext context;

	@Test
	@DisplayName("subscribing commits the response straight away")
	void subscriptionIsAcknowledgedBeforeAnythingHappens() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

		MvcResult result = mvc.perform(get("/api/incidents/stream")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getContentAsString())
				.as("the browser needs bytes before it will admit the stream is open")
				.contains(":connected");
	}
}
