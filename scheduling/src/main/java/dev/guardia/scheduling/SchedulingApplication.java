package dev.guardia.scheduling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// Shared infrastructure plus this service, and nothing else. Naming the packages rather
// than scanning "dev.guardia" is what lets all three services sit on one classpath — in the
// end-to-end suite — without each of them starting the other two.
@SpringBootApplication(scanBasePackages = {"dev.guardia.platform", "dev.guardia.scheduling"})
@EntityScan({"dev.guardia.platform", "dev.guardia.scheduling"})
@EnableJpaRepositories({"dev.guardia.platform", "dev.guardia.scheduling"})
@EnableScheduling
public class SchedulingApplication {
	public static void main(String[] args) {
		SpringApplication.run(SchedulingApplication.class, args);
	}
}
