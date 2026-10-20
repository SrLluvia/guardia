package dev.guardia.incidents.api;

import dev.guardia.incidents.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequest(
		@NotBlank @Size(max = 64) String customerId,
		@NotNull Priority priority,
		@NotBlank @Size(max = 64) String requiredSkill,
		@NotBlank @Size(max = 280) String summary) {
}
