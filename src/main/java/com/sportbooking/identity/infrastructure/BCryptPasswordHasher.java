package com.sportbooking.identity.infrastructure;

import com.sportbooking.identity.application.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class BCryptPasswordHasher implements PasswordHasher {

	private final PasswordEncoder passwordEncoder;

	BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public String hash(String password) {
		return passwordEncoder.encode(password);
	}

	@Override
	public boolean matches(String password, String passwordHash) {
		return passwordEncoder.matches(password, passwordHash);
	}
}
