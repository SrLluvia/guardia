package dev.guardia.scheduling.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the page read this service's diary.
 *
 * <p>The page is served by the incidents service on another port, so a browser asking this one
 * for the diary is making a cross-origin request. The permission is deliberately narrow: one
 * named origin, the diary path, and GET. A blanket {@code *} would be one line shorter and
 * would also let any page on the internet read it, which is a trade nobody actually wants to
 * make — it is just the one that gets made by accident.
 */
@Configuration
public class PageCorsConfiguration implements WebMvcConfigurer {

	private final String pageOrigin;

	public PageCorsConfiguration(@Value("${guardia.page-origin:http://localhost:8081}") String pageOrigin) {
		this.pageOrigin = pageOrigin;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/slots")
				.allowedOrigins(pageOrigin)
				.allowedMethods("GET");
	}
}
