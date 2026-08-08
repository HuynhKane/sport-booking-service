package com.sportbooking.identity.application;

import java.util.Set;
import java.util.UUID;

import com.sportbooking.identity.application.VenueAccessPolicy.AuthenticatedAccount;
import com.sportbooking.identity.domain.AccountStatus;
import com.sportbooking.identity.domain.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateRecordAccessPolicyTest {

	private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

	private static final UUID OTHER_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");

	private final PrivateRecordAccessPolicy policy = new PrivateRecordAccessPolicy();

	@Test
	void allowsOnlyActiveAccountToAccessItsOwnPrivateRecord() {
		assertThat(policy.canAccess(account(AccountStatus.ACTIVE), USER_ID)).isTrue();
		assertThat(policy.canAccess(account(AccountStatus.ACTIVE), OTHER_USER_ID)).isFalse();
		assertThat(policy.canAccess(account(AccountStatus.DISABLED), USER_ID)).isFalse();
	}

	private AuthenticatedAccount account(AccountStatus status) {
		return new AuthenticatedAccount(USER_ID, status, Set.of(Role.PLAYER));
	}
}
