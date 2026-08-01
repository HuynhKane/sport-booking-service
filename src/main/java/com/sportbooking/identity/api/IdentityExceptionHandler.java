package com.sportbooking.identity.api;

import com.sportbooking.identity.application.AuthenticationFailedException;
import com.sportbooking.identity.application.DuplicateEmailException;
import com.sportbooking.identity.application.InvalidRegistrationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class IdentityExceptionHandler {

	@ExceptionHandler(DuplicateEmailException.class)
	ProblemDetail duplicateEmail() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "An account with this email already exists");
	}

	@ExceptionHandler(AuthenticationFailedException.class)
	ProblemDetail authenticationFailed() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
	}

	@ExceptionHandler({InvalidRegistrationException.class, MethodArgumentNotValidException.class})
	ProblemDetail invalidRequest(Exception exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is invalid");
	}
}
