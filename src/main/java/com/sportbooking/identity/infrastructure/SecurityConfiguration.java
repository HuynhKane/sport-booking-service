package com.sportbooking.identity.infrastructure;

import java.util.Collection;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/error",
								"/actuator/health/**",
								"/actuator/info",
								"/swagger-ui/**",
								"/v3/api-docs/**"
						)
						.permitAll()
						.requestMatchers("/api/v1/accounts", "/api/v1/auth/tokens").permitAll()
						.requestMatchers(
								org.springframework.http.HttpMethod.GET,
								"/api/v1/venues",
								"/api/v1/venues/**",
								"/api/v1/courts/*/availability"
						)
						.permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer.jwt(
						jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
				))
				.build();
	}

	@Bean
	Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(this::authorities);
		return converter;
	}

	private Collection<GrantedAuthority> authorities(Jwt jwt) {
		List<String> roles = jwt.getClaimAsStringList("roles");
		if (roles == null) {
			return List.of();
		}
		return roles.stream()
				.<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
				.toList();
	}
}
