package com.sportbooking.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAccountRepository extends JpaRepository<AccountEntity, UUID> {

	Optional<AccountEntity> findByEmail(String email);
}
