package com.sportbooking.identity.application;

public class InvalidRegistrationException extends RuntimeException {

	public InvalidRegistrationException(String message) {
		super(message);
	}
}
