package dev.guardia.incidents.api;

import dev.guardia.incidents.domain.IncidentRepository;
import dev.guardia.incidents.saga.IncidentSaga;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes state changes to open browsers, so the page shows the saga moving instead of being
 * polled at.
 *
 * <p>Sent after commit, never before: a browser must not be shown a state the database could
 * still roll back. Delivery is best-effort — this is a view, and the page reloads the list on
 * reconnect — so a dead connection is dropped rather than retried.
 */
@Component
public class IncidentStream {

	private static final Logger log = LoggerFactory.getLogger(IncidentStream.class);

	private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();
	private final IncidentRepository incidents;

	public IncidentStream(IncidentRepository incidents) {
		this.incidents = incidents;
	}

	public SseEmitter subscribe() {
		SseEmitter emitter = new SseEmitter(0L); // no timeout; the demo tab stays open
		emitter.onCompletion(() -> subscribers.remove(emitter));
		emitter.onTimeout(() -> subscribers.remove(emitter));
		emitter.onError(e -> subscribers.remove(emitter));

		// A comment, sent immediately and ignored by the client's event handlers, but it
		// commits the response. Without it nothing reaches the browser until the first state
		// change, so EventSource never fires `open` and a page with nothing happening on it
		// cannot tell "connected and quiet" from "not connected".
		try {
			emitter.send(SseEmitter.event().comment("connected"));
		} catch (IOException e) {
			log.debug("subscriber went away before it was greeted");
			emitter.completeWithError(e);
			return emitter;
		}

		subscribers.add(emitter);
		return emitter;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onIncidentChanged(IncidentSaga.IncidentChanged changed) {
		incidents.findById(changed.incidentId())
				.map(IncidentView::of)
				.ifPresent(this::broadcast);
	}

	private void broadcast(IncidentView view) {
		for (SseEmitter subscriber : subscribers) {
			try {
				subscriber.send(SseEmitter.event().name("incident").data(view));
			} catch (IOException | IllegalStateException e) {
				log.debug("dropping a closed subscriber");
				subscribers.remove(subscriber);
			}
		}
	}
}
