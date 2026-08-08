package com.sportbooking.identity.application;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.sportbooking.identity.application.AccessTokenIssuer.IssuedToken;
import com.sportbooking.identity.domain.Account;
import com.sportbooking.identity.domain.AccountStatus;
import com.sportbooking.identity.domain.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationServiceTest {

	private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

	@Test
	void authenticatesActiveAccountAndReturnsBearerToken() {
		Account account = account(AccountStatus.ACTIVE);
		AuthenticationService service = service(Optional.of(account), true);

		AuthenticationService.AuthenticationResult result = service.authenticate(
				" PLAYER@example.com ",
				"password123"
		);

		assertThat(result.accessToken()).isEqualTo("signed.jwt");
		assertThat(result.tokenType()).isEqualTo("Bearer");
		assertThat(result.expiresInSeconds()).isEqualTo(3600);
	}

	@Test
	void usesSameFailureForUnknownEmailWrongPasswordAndDisabledAccount() {
		assertAuthenticationFails(service(Optional.empty(), false));
		assertAuthenticationFails(service(Optional.of(account(AccountStatus.ACTIVE)), false));
		assertAuthenticationFails(service(Optional.of(account(AccountStatus.DISABLED)), true));
	}

	private void assertAuthenticationFails(AuthenticationService service) {
		assertThatThrownBy(() -> service.authenticate("player@example.com", "wrong-password"))
				.isInstanceOf(AuthenticationFailedException.class)
				.hasMessage("Invalid email or password");
	}

	private AuthenticationService service(Optional<Account> account, boolean passwordMatches) {
		AccountRepository repository = new AccountRepository() {
			@Override
			public Optional<Account> findByEmail(String normalizedEmail) {
				assertThat(normalizedEmail).isEqualTo("player@example.com");
				return account;
			}

			@Override
			public Account save(Account value) {
				throw new UnsupportedOperationException();
			}
		};
		PasswordHasher hasher = new PasswordHasher() {
			@Override
			public String hash(String password) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean matches(String password, String passwordHash) {
				return passwordMatches;
			}
		};
		return new AuthenticationService(repository, hasher, value -> new IssuedToken("signed.jwt", 3600));
	}

	private Account account(AccountStatus status) {
		return new Account(USER_ID, "player@example.com", "hash", "Player", status, Set.of(Role.PLAYER));
	}
}
