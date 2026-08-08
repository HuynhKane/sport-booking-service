package com.sportbooking.venue.application;

import com.sportbooking.venue.domain.CourtStatus;
import com.sportbooking.venue.domain.VenueStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingEligibilityPolicyTest {

	private final BookingEligibilityPolicy policy = new BookingEligibilityPolicy();

	@Test
	void acceptsNewPlayerBookingOnlyWhenVenueAndCourtAreActive() {
		assertThat(policy.acceptsNewPlayerBooking(VenueStatus.ACTIVE, CourtStatus.ACTIVE)).isTrue();
		assertThat(policy.acceptsNewPlayerBooking(VenueStatus.DRAFT, CourtStatus.ACTIVE)).isFalse();
		assertThat(policy.acceptsNewPlayerBooking(VenueStatus.INACTIVE, CourtStatus.ACTIVE)).isFalse();
		assertThat(policy.acceptsNewPlayerBooking(VenueStatus.ACTIVE, CourtStatus.INACTIVE)).isFalse();
		assertThat(policy.acceptsNewPlayerBooking(VenueStatus.ACTIVE, CourtStatus.MAINTENANCE)).isFalse();
	}
}
