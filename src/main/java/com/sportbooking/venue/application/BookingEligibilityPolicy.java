package com.sportbooking.venue.application;

import com.sportbooking.venue.domain.CourtStatus;
import com.sportbooking.venue.domain.VenueStatus;

public class BookingEligibilityPolicy {

	public boolean acceptsNewPlayerBooking(VenueStatus venueStatus, CourtStatus courtStatus) {
		return venueStatus == VenueStatus.ACTIVE && courtStatus == CourtStatus.ACTIVE;
	}
}
