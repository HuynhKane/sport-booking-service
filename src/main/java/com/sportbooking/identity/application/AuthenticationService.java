package com.sportbooking.identity.application;

import com.sportbooking.identity.application.AccessTokenIssuer.IssuedToken;
import com.sportbooking.identity.domain.Account;
import com.sportbooking.identity.domain.AccountStatus;

public class AuthenticationService {

	private static final String DUMMY_PASSWORD_HASH =
			"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

	private final AccountRepository accountRepository;

	private final PasswordHasher passwordHasher;

	private final AccessTokenIssuer tokenIssuer;

	public AuthenticationService(
			AccountRepository accountRepository,
			PasswordHasher passwordHasher,
			AccessTokenIssuer tokenIssuer
	) {
		this.accountRepository = accountRepository;
		this.passwordHasher = passwordHasher;
		this.tokenIssuer = tokenIssuer;
	}

	public AuthenticationResult authenticate(String email, String password) {
		String normalizedEmail;
		try {
			normalizedEmail = RegistrationService.normalizeEmail(email);
		} catch (InvalidRegistrationException exception) {
			throw new AuthenticationFailedException();
		}

		Account account = accountRepository.findByEmail(normalizedEmail).orElse(null);
		String storedHash = account == null ? DUMMY_PASSWORD_HASH : account.passwordHash();
		boolean matches = password != null && passwordHasher.matches(password, storedHash);
		if (account == null || account.status() != AccountStatus.ACTIVE || !matches) {
			throw new AuthenticationFailedException();
		}

		IssuedToken token = tokenIssuer.issue(account);
		return new AuthenticationResult(token.value(), "Bearer", token.expiresInSeconds());
	}

	public record AuthenticationResult(String accessToken, String tokenType, long expiresInSeconds) {
	}
}
