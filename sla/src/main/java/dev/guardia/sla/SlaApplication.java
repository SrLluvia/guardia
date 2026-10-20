package dev.guardia.sla;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "dev.guardia")
@EntityScan("dev.guardia")
@EnableJpaRepositories("dev.guardia")
@EnableScheduling
public class SlaApplication {
	public static void main(String[] args) {
		SpringApplication.run(SlaApplication.class, args);
	}
}
