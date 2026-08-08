package com.sportbooking.venue.domain;

import java.util.UUID;

public record Venue(
		UUID id,
		String name,
		String description,
		String phoneNumber,
		String addressLine,
		String ward,
		String district,
		String city,
		Coordinates location,
		String timezone,
		VenueStatus status
) {
	public record Coordinates(double latitude, double longitude) {
	}
}
