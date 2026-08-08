package com.sportbooking.venue.api;

import com.sportbooking.venue.application.InvalidVenueRequestException;
import com.sportbooking.venue.application.VenueAccessDeniedException;
import com.sportbooking.venue.application.VenueConflictException;
import com.sportbooking.venue.application.VenueNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class VenueExceptionHandler {

	@ExceptionHandler(InvalidVenueRequestException.class)
	ProblemDetail invalidRequest() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The venue request is invalid");
	}

	@ExceptionHandler(VenueAccessDeniedException.class)
	ProblemDetail accessDenied() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "The venue resource is not owned by this user");
	}

	@ExceptionHandler(VenueNotFoundException.class)
	ProblemDetail notFound() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The venue resource was not found");
	}

	@ExceptionHandler(VenueConflictException.class)
	ProblemDetail conflict(VenueConflictException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}
}
