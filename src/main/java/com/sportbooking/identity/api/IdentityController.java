package com.sportbooking.identity.api;

import java.net.URI;

import com.sportbooking.identity.application.AuthenticationService;
import com.sportbooking.identity.application.AuthenticationService.AuthenticationResult;
import com.sportbooking.identity.application.RegistrationService;
import com.sportbooking.identity.application.RegistrationService.AccountSummary;
import com.sportbooking.identity.application.RegistrationService.RegisterAccountCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class IdentityController {

	private final RegistrationService registrationService;

	private final AuthenticationService authenticationService;

	IdentityController(RegistrationService registrationService, AuthenticationService authenticationService) {
		this.registrationService = registrationService;
		this.authenticationService = authenticationService;
	}

	@PostMapping("/accounts")
	ResponseEntity<AccountSummary> register(@Valid @RequestBody RegistrationRequest request) {
		AccountSummary account = registrationService.register(new RegisterAccountCommand(
				request.email(),
				request.password(),
				request.displayName(),
				request.role()
		));
		return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.id())).body(account);
	}

	@PostMapping("/auth/tokens")
	AuthenticationResult authenticate(@Valid @RequestBody AuthenticationRequest request) {
		return authenticationService.authenticate(request.email(), request.password());
	}

	record RegistrationRequest(
			@NotBlank @Email @Size(max = 320) String email,
			@NotBlank @Size(min = 8, max = 72) String password,
			@NotBlank @Size(max = 120) String displayName,
			@NotBlank String role
	) {
	}

	record AuthenticationRequest(@NotBlank @Email String email, @NotBlank String password) {
	}
}
