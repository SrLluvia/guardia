package dev.guardia.platform.kafka;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

/**
 * Reads the two headers every event carries: what it is, and which delivery it is.
 *
 * <p>Read by hand rather than through {@code @Header} parameters: byte arrays to typed
 * parameters is a conversion with options, and a missing header should fail here, loudly,
 * on its way to the dead letter topic.
 */
public final class EventHeaders {

	public static final String EVENT_TYPE = "event-type";
	public static final String MESSAGE_ID = "message-id";

	public static String eventType(ConsumerRecord<String, String> record) {
		return required(record, EVENT_TYPE);
	}

	public static UUID messageId(ConsumerRecord<String, String> record) {
		return UUID.fromString(required(record, MESSAGE_ID));
	}

	private static String required(ConsumerRecord<String, String> record, String name) {
		Header header = record.headers().lastHeader(name);
		if (header == null) {
			throw new IllegalArgumentException("record on %s has no %s header".formatted(record.topic(), name));
		}
		return new String(header.value(), StandardCharsets.UTF_8);
	}

	private EventHeaders() {
	}
}
