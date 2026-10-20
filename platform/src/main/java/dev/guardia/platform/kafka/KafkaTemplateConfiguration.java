package dev.guardia.platform.kafka;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * A producer of JSON text, declared rather than inherited.
 *
 * <p>Boot's own template is typed {@code KafkaTemplate<?, ?>}, which does not reliably satisfy
 * an injection point asking for {@code <String, String>}. Declaring it here also makes the
 * shape of what leaves this system explicit: keys and values are both strings, because the
 * payload is the JSON the outbox stored and the key is an aggregate id.
 */
@Configuration
public class KafkaTemplateConfiguration {

	@Bean
	public ProducerFactory<String, String> stringProducerFactory(KafkaProperties properties) {
		return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties());
	}

	@Bean
	public KafkaTemplate<String, String> stringKafkaTemplate(ProducerFactory<String, String> producerFactory) {
		return new KafkaTemplate<>(producerFactory);
	}
}
