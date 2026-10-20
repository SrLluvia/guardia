package dev.guardia.sla.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/** How long a customer has been promised they will wait, at one priority. */
@Entity
@Table(name = "contract")
@IdClass(Contract.Key.class)
public class Contract {

	@Id
	@Column(name = "customer_id", nullable = false, updatable = false)
	private String customerId;

	@Id
	@Column(nullable = false, updatable = false)
	private String priority;

	@Column(name = "response_minutes", nullable = false)
	private int responseMinutes;

	protected Contract() {
	}

	public int responseMinutes() {
		return responseMinutes;
	}

	public record Key(String customerId, String priority) implements Serializable {
		public Key {
			Objects.requireNonNull(customerId);
			Objects.requireNonNull(priority);
		}
	}
}
