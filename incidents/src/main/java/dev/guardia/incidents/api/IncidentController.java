package dev.guardia.incidents.api;

import dev.guardia.incidents.domain.Incident;
import dev.guardia.incidents.domain.IncidentRepository;
import dev.guardia.incidents.saga.IncidentSaga;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

	private final IncidentSaga saga;
	private final IncidentRepository incidents;
	private final IncidentStream stream;

	public IncidentController(IncidentSaga saga, IncidentRepository incidents, IncidentStream stream) {
		this.saga = saga;
		this.incidents = incidents;
		this.stream = stream;
	}

	/**
	 * Accepts the incident and returns at once.
	 *
	 * <p>202, not 201: what comes back is a saga that has started, not an assignment that
	 * exists. Telling the caller otherwise would be a lie it could act on.
	 */
	@PostMapping
	public ResponseEntity<IncidentView> open(@Valid @RequestBody CreateIncidentRequest request) {
		Incident incident = saga.open(request.customerId(), request.priority(), request.requiredSkill(), request.summary());
		return ResponseEntity.accepted().body(IncidentView.of(incident));
	}

	@GetMapping
	public List<IncidentView> recent() {
		return incidents.findAllByOrderByCreatedAtDesc(Limit.of(50)).stream().map(IncidentView::of).toList();
	}

	@GetMapping("/{id}")
	public ResponseEntity<IncidentView> byId(@PathVariable UUID id) {
		return incidents.findById(id).map(IncidentView::of).map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream() {
		return stream.subscribe();
	}
}
