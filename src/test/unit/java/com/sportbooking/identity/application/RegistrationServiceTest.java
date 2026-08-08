package com.sportbooking.identity.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.sportbooking.identity.application.RegistrationService.AccountSummary;
import com.sportbooking.identity.application.RegistrationService.RegisterAccountCommand;
import com.sportbooking.identity.domain.Account;
import com.sportbooking.identity.domain.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationServiceTest {

	private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();

	private final RecordingPasswordHasher passwords = new RecordingPasswordHasher();

	private final RegistrationService service = new RegistrationService(accounts, passwords);

	@Test
	void registersAccountWithNormalizedEmailHashedPasswordAndSelectedRole() {
		AccountSummary result = service.register(new RegisterAccountCommand(
				"  Player@Example.COM ",
				"password123",
				"Khanh",
				"PLAYER"
		));

		assertThat(result.email()).isEqualTo("player@example.com");
		assertThat(result.displayName()).isEqualTo("Khanh");
		assertThat(result.roles()).containsExactly(Role.PLAYER);
		assertThat(accounts.saved.passwordHash()).isEqualTo("hashed:password123");
		assertThat(passwords.hashedValues).containsExactly("password123");
	}

	@Test
	void rejectsDuplicateEmailWithoutSavingAccount() {
		accounts.existing = sampleAccount("player@example.com");

		assertThatThrownBy(() -> service.register(new RegisterAccountCommand(
				"PLAYER@example.com",
				"password123",
				"Other Player",
				"PLAYER"
		))).isInstanceOf(DuplicateEmailException.class);
		assertThat(accounts.saved).isNull();
	}

	@Test
	void rejectsUnsupportedRoleWithoutSavingAccount() {
		assertThatThrownBy(() -> service.register(new RegisterAccountCommand(
				"player@example.com",
				"password123",
				"Player",
				"ADMIN"
		))).isInstanceOf(InvalidRegistrationException.class);
		assertThat(accounts.saved).isNull();
	}

	private Account sampleAccount(String email) {
		return new Account(null, email, "hash", "Player", null, java.util.Set.of(Role.PLAYER));
	}

	private static final class InMemoryAccountRepository implements AccountRepository {
		private Account existing;

		private Account saved;

		@Override
		public Optional<Account> findByEmail(String normalizedEmail) {
			return Optional.ofNullable(existing);
		}

		@Override
		public Account save(Account account) {
			saved = account;
			return account;
		}
	}

	private static final class RecordingPasswordHasher implements PasswordHasher {
		private final List<String> hashedValues = new ArrayList<>();

		@Override
		public String hash(String password) {
			hashedValues.add(password);
			return "hashed:" + password;
		}

		@Override
		public boolean matches(String password, String passwordHash) {
			return false;
		}
	}
}
