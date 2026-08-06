package com.sportbooking.identity.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.sportbooking.identity.application.AccessTokenIssuer;
import com.sportbooking.identity.domain.Account;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

	private final JwtEncoder jwtEncoder;

	private final Clock clock;

	private final Duration timeToLive;

	JwtAccessTokenIssuer(JwtEncoder jwtEncoder, Clock clock, @Value("${security.jwt.ttl}") Duration timeToLive) {
		this.jwtEncoder = jwtEncoder;
		this.clock = clock;
		this.timeToLive = timeToLive;
	}

	@Override
	public IssuedToken issue(Account account) {
		Instant issuedAt = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("sport-booking-service")
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plus(timeToLive))
				.subject(account.id().toString())
				.claim("roles", account.roles().stream().map(Enum::name).sorted().toList())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, timeToLive.toSeconds());
	}
}
