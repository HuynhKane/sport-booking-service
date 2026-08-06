package com.sportbooking.identity.application;

import java.util.Set;
import java.util.UUID;

import com.sportbooking.identity.application.VenueAccessPolicy.AuthenticatedAccount;
import com.sportbooking.identity.domain.AccountStatus;
import com.sportbooking.identity.domain.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VenueAccessPolicyTest {

	private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

	private static final UUID VENUE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

	@Test
	void allowsOnlyActiveAssociatedOwnerToManageVenue() {
		VenueAccessPolicy ownedVenue = new VenueAccessPolicy((userId, venueId) -> true);
		VenueAccessPolicy unownedVenue = new VenueAccessPolicy((userId, venueId) -> false);

		assertThat(ownedVenue.canManage(account(AccountStatus.ACTIVE, Role.OWNER), VENUE_ID)).isTrue();
		assertThat(unownedVenue.canManage(account(AccountStatus.ACTIVE, Role.OWNER), VENUE_ID)).isFalse();
		assertThat(ownedVenue.canManage(account(AccountStatus.ACTIVE, Role.PLAYER), VENUE_ID)).isFalse();
		assertThat(ownedVenue.canManage(account(AccountStatus.DISABLED, Role.OWNER), VENUE_ID)).isFalse();
	}

	private AuthenticatedAccount account(AccountStatus status, Role role) {
		return new AuthenticatedAccount(USER_ID, status, Set.of(role));
	}
}
