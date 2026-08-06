package com.sportbooking.identity.application;

import java.util.UUID;

import com.sportbooking.identity.application.VenueAccessPolicy.AuthenticatedAccount;
import com.sportbooking.identity.domain.AccountStatus;

public class PrivateRecordAccessPolicy {

	public boolean canAccess(AuthenticatedAccount account, UUID recordOwnerUserId) {
		return account != null
				&& account.status() == AccountStatus.ACTIVE
				&& account.userId().equals(recordOwnerUserId);
	}
}
