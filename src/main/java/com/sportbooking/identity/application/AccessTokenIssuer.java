package com.sportbooking.identity.application;

import com.sportbooking.identity.domain.Account;

public interface AccessTokenIssuer {

	IssuedToken issue(Account account);

	record IssuedToken(String value, long expiresInSeconds) {
	}
}
