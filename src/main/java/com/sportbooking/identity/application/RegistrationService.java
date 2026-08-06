package com.sportbooking.identity.application;

import java.util.Set;
import java.util.UUID;

import com.sportbooking.identity.domain.Account;
import com.sportbooking.identity.domain.AccountStatus;
import com.sportbooking.identity.domain.Role;

public class RegistrationService {

	private final AccountRepository accountRepository;

	private final PasswordHasher passwordHasher;

	public RegistrationService(AccountRepository accountRepository, PasswordHasher passwordHasher) {
		this.accountRepository = accountRepository;
		this.passwordHasher = passwordHasher;
	}

	public AccountSummary register(RegisterAccountCommand command) {
		String email = normalizeEmail(command.email());
		String displayName = requireDisplayName(command.displayName());
		String password = requirePassword(command.password());
		Role role = requireRole(command.role());

		if (accountRepository.findByEmail(email).isPresent()) {
			throw new DuplicateEmailException();
		}

		Account account = new Account(
				UUID.randomUUID(),
				email,
				passwordHasher.hash(password),
				displayName,
				AccountStatus.ACTIVE,
				Set.of(role)
		);
		Account saved = accountRepository.save(account);
		return new AccountSummary(saved.id(), saved.email(), saved.displayName(), saved.roles());
	}

	static String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new InvalidRegistrationException("Email is required");
		}
		String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
		if (normalized.length() > 320 || !normalized.contains("@")) {
			throw new InvalidRegistrationException("Email is invalid");
		}
		return normalized;
	}

	private String requireDisplayName(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			throw new InvalidRegistrationException("Display name is required");
		}
		return displayName.trim();
	}

	private String requirePassword(String password) {
		if (password == null || password.length() < 8 || password.length() > 72) {
			throw new InvalidRegistrationException("Password must contain 8 to 72 characters");
		}
		return password;
	}

	private Role requireRole(String role) {
		try {
			return Role.valueOf(role == null ? "" : role.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new InvalidRegistrationException("Role must be PLAYER or OWNER");
		}
	}

	public record RegisterAccountCommand(String email, String password, String displayName, String role) {
	}

	public record AccountSummary(UUID id, String email, String displayName, Set<Role> roles) {
	}
}
