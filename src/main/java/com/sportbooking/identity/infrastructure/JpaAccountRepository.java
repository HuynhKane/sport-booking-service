package com.sportbooking.identity.infrastructure;

import java.util.Optional;

import com.sportbooking.identity.application.AccountRepository;
import com.sportbooking.identity.application.DuplicateEmailException;
import com.sportbooking.identity.domain.Account;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class JpaAccountRepository implements AccountRepository {

	private final SpringDataAccountRepository repository;

	JpaAccountRepository(SpringDataAccountRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<Account> findByEmail(String normalizedEmail) {
		return repository.findByEmail(normalizedEmail).map(this::toDomain);
	}

	@Override
	public Account save(Account account) {
		AccountEntity entity = new AccountEntity(
				account.id(),
				account.email(),
				account.passwordHash(),
				account.displayName(),
				account.status(),
				account.roles()
		);
		try {
			return toDomain(repository.saveAndFlush(entity));
		} catch (DataIntegrityViolationException exception) {
			throw new DuplicateEmailException();
		}
	}

	private Account toDomain(AccountEntity entity) {
		return new Account(
				entity.getId(),
				entity.getEmail(),
				entity.getPasswordHash(),
				entity.getDisplayName(),
				entity.getStatus(),
				entity.getRoles()
		);
	}
}
