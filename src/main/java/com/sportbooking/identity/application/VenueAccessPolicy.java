package com.sportbooking.identity.application;

import java.util.Set;
import java.util.UUID;

import com.sportbooking.identity.domain.AccountStatus;
import com.sportbooking.identity.domain.Role;

public class VenueAccessPolicy {

	private final VenueOwnership venueOwnership;

	public VenueAccessPolicy(VenueOwnership venueOwnership) {
		this.venueOwnership = venueOwnership;
	}

	public boolean canManage(AuthenticatedAccount account, UUID venueId) {
		return account != null
				&& account.status() == AccountStatus.ACTIVE
				&& account.roles().contains(Role.OWNER)
				&& venueOwnership.isOwner(account.userId(), venueId);
	}

	public interface VenueOwnership {
		boolean isOwner(UUID userId, UUID venueId);
	}

	public record AuthenticatedAccount(UUID userId, AccountStatus status, Set<Role> roles) {
	}
}
