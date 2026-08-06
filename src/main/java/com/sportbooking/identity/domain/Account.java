package com.sportbooking.identity.domain;

import java.util.Set;
import java.util.UUID;

public record Account(
		UUID id,
		String email,
		String passwordHash,
		String displayName,
		AccountStatus status,
		Set<Role> roles
) {
}
