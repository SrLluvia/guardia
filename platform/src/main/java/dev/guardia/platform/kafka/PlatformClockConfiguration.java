package dev.guardia.platform.kafka;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A clock as a bean, so tests can move time instead of sleeping through it. */
@Configuration
public class PlatformClockConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
