package com.sportbooking.venue.application;

public class InvalidVenueRequestException extends RuntimeException {

	public InvalidVenueRequestException(String message) {
		super(message);
	}
}
