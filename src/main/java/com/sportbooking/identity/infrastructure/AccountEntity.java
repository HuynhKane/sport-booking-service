package com.sportbooking.identity.infrastructure;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.sportbooking.identity.domain.AccountStatus;
import com.sportbooking.identity.domain.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
class AccountEntity {

	@Id
	private UUID id;

	@Column(nullable = false, length = 320)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "display_name", nullable = false, length = 120)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountStatus status;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Set<Role> roles = new HashSet<>();

	protected AccountEntity() {
	}

	AccountEntity(
			UUID id,
			String email,
			String passwordHash,
			String displayName,
			AccountStatus status,
			Set<Role> roles
	) {
		this.id = id;
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.status = status;
		this.roles = new HashSet<>(roles);
	}

	UUID getId() {
		return id;
	}

	String getEmail() {
		return email;
	}

	String getPasswordHash() {
		return passwordHash;
	}

	String getDisplayName() {
		return displayName;
	}

	AccountStatus getStatus() {
		return status;
	}

	Set<Role> getRoles() {
		return Set.copyOf(roles);
	}
}
