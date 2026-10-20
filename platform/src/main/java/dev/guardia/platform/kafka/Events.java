package dev.guardia.platform.kafka;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Reads an incoming payload into a type the consumer owns.
 *
 * <p>Deserialisation is explicit and local on purpose. Spring's type headers would let a
 * producer name the class a consumer must have, which quietly turns two services into one
 * deployable. Here each service declares the fields it needs and ignores the rest, so a
 * producer can add a field without anyone else recompiling.
 */
@Component
public class Events {

	private final ObjectMapper objectMapper;

	public Events(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public <T> T read(String payload, Class<T> type) {
		try {
			return objectMapper.readValue(payload, type);
		} catch (Exception e) {
			// Unrecoverable by definition: the same bytes will fail the same way on every
			// retry, so this is the path that ends in the dead letter topic.
			throw new UnreadableEventException(type, e);
		}
	}

	public static class UnreadableEventException extends RuntimeException {
		UnreadableEventException(Class<?> type, Throwable cause) {
			super("payload cannot be read as " + type.getSimpleName(), cause);
		}
	}
}
