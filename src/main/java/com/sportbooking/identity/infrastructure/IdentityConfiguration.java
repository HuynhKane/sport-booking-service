package com.sportbooking.identity.infrastructure;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.sportbooking.identity.application.AccessTokenIssuer;
import com.sportbooking.identity.application.AccountRepository;
import com.sportbooking.identity.application.AuthenticationService;
import com.sportbooking.identity.application.PasswordHasher;
import com.sportbooking.identity.application.RegistrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
class IdentityConfiguration {

	private static final String LOCAL_JWT_SECRET = "local-development-secret-key-32-bytes";

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	RegistrationService registrationService(AccountRepository repository, PasswordHasher passwordHasher) {
		return new RegistrationService(repository, passwordHasher);
	}

	@Bean
	AuthenticationService authenticationService(
			AccountRepository repository,
			PasswordHasher passwordHasher,
			AccessTokenIssuer tokenIssuer
	) {
		return new AuthenticationService(repository, passwordHasher, tokenIssuer);
	}

	@Bean
	SecretKey jwtSecretKey(
			@Value("${security.jwt.secret}") String secret,
			@Value("${RENDER:false}") boolean renderEnvironment
	) {
		if (renderEnvironment && LOCAL_JWT_SECRET.equals(secret)) {
			throw new IllegalStateException("JWT_SECRET must be configured on Render");
		}
		if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
		}
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey secretKey) {
		return NimbusJwtEncoder.withSecretKey(secretKey).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey secretKey) {
		return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
	}
}
