package dev.guardia.incidents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// Shared infrastructure plus this service, and nothing else. Naming the packages rather
// than scanning "dev.guardia" is what lets all three services sit on one classpath — in the
// end-to-end suite — without each of them starting the other two.
@SpringBootApplication(scanBasePackages = {"dev.guardia.platform", "dev.guardia.incidents"})
@EntityScan({"dev.guardia.platform", "dev.guardia.incidents"})
@EnableJpaRepositories({"dev.guardia.platform", "dev.guardia.incidents"})
@EnableScheduling
public class IncidentsApplication {
	public static void main(String[] args) {
		SpringApplication.run(IncidentsApplication.class, args);
	}
}
