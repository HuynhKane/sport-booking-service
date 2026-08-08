package com.sportbooking.venue.application;

import java.time.Instant;
import java.time.LocalTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.sportbooking.venue.domain.Court;
import com.sportbooking.venue.domain.CourtBlock;
import com.sportbooking.venue.domain.CourtStatus;
import com.sportbooking.venue.domain.OperatingPeriod;
import com.sportbooking.venue.domain.Venue;
import com.sportbooking.venue.domain.VenueStatus;

public class VenueManagementService {

	private static final String DEFAULT_CITY = "Ho Chi Minh City";

	private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";

	private final VenueCatalogRepository repository;

	private final Clock clock;

	public VenueManagementService(VenueCatalogRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public Venue createVenue(UUID ownerId, CreateVenueCommand command) {
		Objects.requireNonNull(ownerId, "ownerId");
		Objects.requireNonNull(command, "command");
		validateCoordinates(command.latitude(), command.longitude());
		Venue venue = new Venue(
				UUID.randomUUID(),
				requireText(command.name(), "Venue name", 160),
				optionalText(command.description(), 2000),
				optionalText(command.phoneNumber(), 30),
				requireText(command.addressLine(), "Address", 255),
				optionalText(command.ward(), 120),
				requireText(command.district(), "District", 120),
				DEFAULT_CITY,
				new Venue.Coordinates(command.latitude(), command.longitude()),
				DEFAULT_TIMEZONE,
				VenueStatus.DRAFT
		);
		return repository.createVenue(venue, ownerId);
	}

	public Venue updateVenue(UUID ownerId, UUID venueId, UpdateVenueCommand command) {
		Objects.requireNonNull(command, "command");
		Venue current = ownedVenue(ownerId, venueId);
		if (command.name() == null && command.description() == null
				&& command.phoneNumber() == null && command.status() == null) {
			throw new InvalidVenueRequestException("At least one venue field is required");
		}
		Venue updated = new Venue(
				current.id(),
				command.name() == null ? current.name() : requireText(command.name(), "Venue name", 160),
				command.description() == null ? current.description() : optionalText(command.description(), 2000),
				command.phoneNumber() == null ? current.phoneNumber() : optionalText(command.phoneNumber(), 30),
				current.addressLine(),
				current.ward(),
				current.district(),
				current.city(),
				current.location(),
				current.timezone(),
				command.status() == null ? current.status() : command.status()
		);
		return repository.saveVenue(updated);
	}

	public Court createCourt(UUID ownerId, UUID venueId, CreateCourtCommand command) {
		ownedVenue(ownerId, venueId);
		Objects.requireNonNull(command, "command");
		Court court = new Court(
				UUID.randomUUID(),
				venueId,
				requireText(command.name(), "Court name", 100),
				optionalText(command.description(), 1000),
				CourtStatus.ACTIVE
		);
		return repository.createCourt(court);
	}

	public Court updateCourt(UUID ownerId, UUID courtId, UpdateCourtCommand command) {
		Objects.requireNonNull(command, "command");
		Court current = repository.findCourt(courtId).orElseThrow(VenueNotFoundException::new);
		requireOwnership(current.venueId(), ownerId);
		if (command.name() == null && command.description() == null && command.status() == null) {
			throw new InvalidVenueRequestException("At least one court field is required");
		}
		Court updated = new Court(
				current.id(),
				current.venueId(),
				command.name() == null ? current.name() : requireText(command.name(), "Court name", 100),
				command.description() == null ? current.description() : optionalText(command.description(), 1000),
				command.status() == null ? current.status() : command.status()
		);
		return repository.saveCourt(updated);
	}

	public List<OperatingPeriod> replaceOperatingHours(
			UUID ownerId,
			UUID venueId,
			List<OperatingPeriodCommand> periods
	) {
		ownedVenue(ownerId, venueId);
		if (periods == null) {
			throw new InvalidVenueRequestException("Operating periods are required");
		}
		List<OperatingPeriod> validated = new ArrayList<>();
		for (OperatingPeriodCommand period : periods) {
			if (period == null || period.dayOfWeek() < 1 || period.dayOfWeek() > 7
					|| period.opensAt() == null || period.closesAt() == null
					|| !period.closesAt().isAfter(period.opensAt())) {
				throw new InvalidVenueRequestException("Operating period is invalid");
			}
			validated.add(new OperatingPeriod(period.dayOfWeek(), period.opensAt(), period.closesAt()));
		}
		validated.sort(Comparator.comparingInt(OperatingPeriod::dayOfWeek)
				.thenComparing(OperatingPeriod::opensAt)
				.thenComparing(OperatingPeriod::closesAt));
		for (int index = 1; index < validated.size(); index++) {
			OperatingPeriod previous = validated.get(index - 1);
			OperatingPeriod current = validated.get(index);
			if (previous.dayOfWeek() == current.dayOfWeek()
					&& current.opensAt().isBefore(previous.closesAt())) {
				throw new InvalidVenueRequestException("Operating periods overlap");
			}
		}
		List<OperatingPeriod> result = List.copyOf(validated);
		repository.replaceOperatingHours(venueId, result);
		return result;
	}

	public CourtBlock createCourtBlock(UUID ownerId, UUID courtId, CreateCourtBlockCommand command) {
		Objects.requireNonNull(command, "command");
		Court court = repository.findCourt(courtId).orElseThrow(VenueNotFoundException::new);
		requireOwnership(court.venueId(), ownerId);
		if (command.startsAt() == null || command.endsAt() == null
				|| !command.endsAt().isAfter(command.startsAt())) {
			throw new InvalidVenueRequestException("Court block interval is invalid");
		}
		if (repository.hasActiveBookingOverlap(courtId, command.startsAt(), command.endsAt())) {
			throw new VenueConflictException("Court block conflicts with an active booking");
		}
		CourtBlock block = new CourtBlock(
				UUID.randomUUID(),
				courtId,
				command.startsAt(),
				command.endsAt(),
				optionalText(command.reason(), 255),
				ownerId,
				clock.instant()
		);
		return repository.createCourtBlock(block);
	}

	private Venue ownedVenue(UUID ownerId, UUID venueId) {
		Venue venue = repository.findVenue(venueId).orElseThrow(VenueNotFoundException::new);
		requireOwnership(venueId, ownerId);
		return venue;
	}

	private void requireOwnership(UUID venueId, UUID ownerId) {
		if (!repository.isVenueOwner(venueId, ownerId)) {
			throw new VenueAccessDeniedException();
		}
	}

	private void validateCoordinates(double latitude, double longitude) {
		if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
				|| latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
			throw new InvalidVenueRequestException("Coordinates are invalid");
		}
	}

	private String requireText(String value, String field, int maximumLength) {
		if (value == null || value.isBlank()) {
			throw new InvalidVenueRequestException(field + " is required");
		}
		String trimmed = value.trim();
		if (trimmed.length() > maximumLength) {
			throw new InvalidVenueRequestException(field + " is too long");
		}
		return trimmed;
	}

	private String optionalText(String value, int maximumLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.length() > maximumLength) {
			throw new InvalidVenueRequestException("Text is too long");
		}
		return trimmed;
	}

	public record CreateVenueCommand(
			String name,
			String description,
			String phoneNumber,
			String addressLine,
			String ward,
			String district,
			double latitude,
			double longitude
	) {
	}

	public record UpdateVenueCommand(String name, String description, String phoneNumber, VenueStatus status) {
	}

	public record CreateCourtCommand(String name, String description) {
	}

	public record UpdateCourtCommand(String name, String description, CourtStatus status) {
	}

	public record OperatingPeriodCommand(int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
	}

	public record CreateCourtBlockCommand(Instant startsAt, Instant endsAt, String reason) {
	}
}
