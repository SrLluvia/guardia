package dev.guardia.platform.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * What happens when a consumer throws.
 *
 * <p>A transient fault — the database blinked, a peer was restarting — deserves another go,
 * so the record is retried with a widening gap. A record that fails every time is a poison
 * pill: without somewhere to put it, it would sit at the head of its partition and stop every
 * later record behind it. After the last attempt it is copied to {@code <topic>.DLT} and the
 * partition moves on, which keeps one bad message from becoming an outage.
 */
@Configuration
public class KafkaErrorHandlingConfiguration {

	@Bean
	public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
		// The dead letter keeps the original key, so a dropped event stays traceable to its saga.
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
				kafkaTemplate,
				(record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));

		ExponentialBackOff backOff = new ExponentialBackOff(200L, 2.0);
		backOff.setMaxElapsedTime(5_000L);
		return new DefaultErrorHandler(recoverer, backOff);
	}
}
