package com.sportbooking.identity.application;

import java.util.Optional;

import com.sportbooking.identity.domain.Account;

public interface AccountRepository {

	Optional<Account> findByEmail(String normalizedEmail);

	Account save(Account account);
}
