package dev.guardia.scheduling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "technician")
public class Technician {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String skill;

	protected Technician() {
	}

	public UUID id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String skill() {
		return skill;
	}
}
