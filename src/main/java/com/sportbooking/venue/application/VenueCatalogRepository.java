package com.sportbooking.venue.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sportbooking.venue.domain.Court;
import com.sportbooking.venue.domain.CourtBlock;
import com.sportbooking.venue.domain.OperatingPeriod;
import com.sportbooking.venue.domain.Venue;

public interface VenueCatalogRepository {

	Venue createVenue(Venue venue, UUID ownerId);

	Optional<Venue> findVenue(UUID venueId);

	Venue saveVenue(Venue venue);

	boolean isVenueOwner(UUID venueId, UUID userId);

	Court createCourt(Court court);

	Optional<Court> findCourt(UUID courtId);

	Court saveCourt(Court court);

	void replaceOperatingHours(UUID venueId, List<OperatingPeriod> periods);

	boolean hasActiveBookingOverlap(UUID courtId, Instant startsAt, Instant endsAt);

	CourtBlock createCourtBlock(CourtBlock block);
}
