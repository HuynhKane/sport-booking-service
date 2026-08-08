package com.sportbooking.venue.application;

import java.time.Instant;
import java.time.LocalTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.sportbooking.venue.application.VenueManagementService.CreateCourtBlockCommand;
import com.sportbooking.venue.application.VenueManagementService.CreateCourtCommand;
import com.sportbooking.venue.application.VenueManagementService.CreateVenueCommand;
import com.sportbooking.venue.application.VenueManagementService.OperatingPeriodCommand;
import com.sportbooking.venue.application.VenueManagementService.UpdateVenueCommand;
import com.sportbooking.venue.application.VenueManagementService.UpdateCourtCommand;
import com.sportbooking.venue.domain.Court;
import com.sportbooking.venue.domain.CourtBlock;
import com.sportbooking.venue.domain.CourtStatus;
import com.sportbooking.venue.domain.OperatingPeriod;
import com.sportbooking.venue.domain.Venue;
import com.sportbooking.venue.domain.VenueStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VenueManagementServiceTest {

	private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private FakeVenueCatalogRepository repository;

	private VenueManagementService service;

	@BeforeEach
	void setUp() {
		repository = new FakeVenueCatalogRepository();
		service = new VenueManagementService(
				repository,
				Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)
		);
	}

	@Test
	void createsDraftVenueWithMarketDefaultsAndOwnerAssociation() {
		Venue venue = service.createVenue(OWNER_ID, validVenueCommand());

		assertThat(venue.status()).isEqualTo(VenueStatus.DRAFT);
		assertThat(venue.city()).isEqualTo("Ho Chi Minh City");
		assertThat(venue.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
		assertThat(venue.name()).isEqualTo("Pars Badminton");
		assertThat(repository.ownerByVenue).containsEntry(venue.id(), OWNER_ID);
	}

	@Test
	void rejectsInvalidCoordinatesWithoutSavingVenue() {
		CreateVenueCommand command = new CreateVenueCommand(
				"Pars", null, null, "1 Nguyen Hue", null, "District 1", 91, 106.7
		);

		assertThatThrownBy(() -> service.createVenue(OWNER_ID, command))
				.isInstanceOf(InvalidVenueRequestException.class);
		assertThat(repository.venues).isEmpty();
	}

	@Test
	void allowsOnlyAssociatedOwnerToActivateVenue() {
		Venue venue = repository.givenVenue(OWNER_ID);

		Venue updated = service.updateVenue(
				OWNER_ID,
				venue.id(),
				new UpdateVenueCommand("Updated venue", null, null, VenueStatus.ACTIVE)
		);

		assertThat(updated.name()).isEqualTo("Updated venue");
		assertThat(updated.status()).isEqualTo(VenueStatus.ACTIVE);
		assertThatThrownBy(() -> service.updateVenue(
				UUID.randomUUID(), venue.id(), new UpdateVenueCommand(null, null, null, VenueStatus.INACTIVE)
		)).isInstanceOf(VenueAccessDeniedException.class);
	}

	@Test
	void createsActiveCourtForOwnedVenue() {
		Venue venue = repository.givenVenue(OWNER_ID);

		Court court = service.createCourt(OWNER_ID, venue.id(), new CreateCourtCommand("Court 1", null));

		assertThat(court.venueId()).isEqualTo(venue.id());
		assertThat(court.status()).isEqualTo(CourtStatus.ACTIVE);
		assertThat(repository.courts).containsKey(court.id());
	}

	@Test
	void associatedOwnerUpdatesCourtStatusWithoutChangingItsVenue() {
		Court court = repository.givenCourt(OWNER_ID);

		Court updated = service.updateCourt(
				OWNER_ID,
				court.id(),
				new UpdateCourtCommand("Court A", "Resurfacing", CourtStatus.MAINTENANCE)
		);

		assertThat(updated.venueId()).isEqualTo(court.venueId());
		assertThat(updated.name()).isEqualTo("Court A");
		assertThat(updated.status()).isEqualTo(CourtStatus.MAINTENANCE);
	}

	@Test
	void atomicallyReplacesValidWeeklyHoursAndAllowsAdjacentPeriods() {
		Venue venue = repository.givenVenue(OWNER_ID);
		List<OperatingPeriodCommand> requested = List.of(
				new OperatingPeriodCommand(1, LocalTime.of(6, 0), LocalTime.NOON),
				new OperatingPeriodCommand(1, LocalTime.NOON, LocalTime.of(22, 0)),
				new OperatingPeriodCommand(7, LocalTime.of(8, 0), LocalTime.of(18, 0))
		);

		List<OperatingPeriod> result = service.replaceOperatingHours(OWNER_ID, venue.id(), requested);

		assertThat(result).hasSize(3);
		assertThat(repository.operatingHours).containsExactlyElementsOf(result);
	}

	@Test
	void emptyWeeklyReplacementClosesTheRegularSchedule() {
		Venue venue = repository.givenVenue(OWNER_ID);
		repository.operatingHours = List.of(
				new OperatingPeriod(1, LocalTime.of(6, 0), LocalTime.of(22, 0))
		);

		List<OperatingPeriod> result = service.replaceOperatingHours(OWNER_ID, venue.id(), List.of());

		assertThat(result).isEmpty();
		assertThat(repository.operatingHours).isEmpty();
	}

	@Test
	void rejectsOverlappingWeeklyHoursWithoutReplacingExistingSchedule() {
		Venue venue = repository.givenVenue(OWNER_ID);
		repository.operatingHours = List.of(new OperatingPeriod(2, LocalTime.of(8, 0), LocalTime.of(18, 0)));
		List<OperatingPeriodCommand> overlapping = List.of(
				new OperatingPeriodCommand(1, LocalTime.of(6, 0), LocalTime.NOON),
				new OperatingPeriodCommand(1, LocalTime.of(11, 0), LocalTime.of(18, 0))
		);

		assertThatThrownBy(() -> service.replaceOperatingHours(OWNER_ID, venue.id(), overlapping))
				.isInstanceOf(InvalidVenueRequestException.class);
		assertThat(repository.operatingHours)
				.containsExactly(new OperatingPeriod(2, LocalTime.of(8, 0), LocalTime.of(18, 0)));
	}

	@Test
	void rejectsInvalidWeekdayAndNonIncreasingOperatingTimes() {
		Venue venue = repository.givenVenue(OWNER_ID);

		assertThatThrownBy(() -> service.replaceOperatingHours(
				OWNER_ID,
				venue.id(),
				List.of(new OperatingPeriodCommand(0, LocalTime.of(8, 0), LocalTime.of(18, 0)))
		)).isInstanceOf(InvalidVenueRequestException.class);
		assertThatThrownBy(() -> service.replaceOperatingHours(
				OWNER_ID,
				venue.id(),
				List.of(new OperatingPeriodCommand(1, LocalTime.of(18, 0), LocalTime.of(8, 0)))
		)).isInstanceOf(InvalidVenueRequestException.class);
		assertThatThrownBy(() -> service.replaceOperatingHours(
				OWNER_ID,
				venue.id(),
				List.of(new OperatingPeriodCommand(1, LocalTime.of(8, 0), LocalTime.of(8, 0)))
		)).isInstanceOf(InvalidVenueRequestException.class);
		assertThat(repository.operatingHours).isEmpty();
	}

	@Test
	void createsCourtBlockForOwnerWhenNoActiveBookingOverlaps() {
		Court court = repository.givenCourt(OWNER_ID);
		Instant startsAt = Instant.parse("2030-01-01T10:00:00Z");
		Instant endsAt = Instant.parse("2030-01-01T11:00:00Z");

		CourtBlock block = service.createCourtBlock(
				OWNER_ID, court.id(), new CreateCourtBlockCommand(startsAt, endsAt, "Maintenance")
		);

		assertThat(block.createdByUserId()).isEqualTo(OWNER_ID);
		assertThat(block.reason()).isEqualTo("Maintenance");
		assertThat(repository.blocks).contains(block);
	}

	@Test
	void rejectsCourtBlockWhenActiveBookingOverlaps() {
		Court court = repository.givenCourt(OWNER_ID);
		repository.bookingOverlap = true;

		assertThatThrownBy(() -> service.createCourtBlock(
				OWNER_ID,
				court.id(),
				new CreateCourtBlockCommand(
						Instant.parse("2030-01-01T10:00:00Z"),
						Instant.parse("2030-01-01T11:00:00Z"),
						"Private event"
				)
		)).isInstanceOf(VenueConflictException.class);
		assertThat(repository.blocks).isEmpty();
	}

	private CreateVenueCommand validVenueCommand() {
		return new CreateVenueCommand(
				" Pars Badminton ",
				"Community courts",
				"0900000000",
				"1 Nguyen Hue",
				"Ben Nghe",
				"District 1",
				10.78,
				106.7
		);
	}

	private static final class FakeVenueCatalogRepository implements VenueCatalogRepository {

		private final Map<UUID, Venue> venues = new HashMap<>();

		private final Map<UUID, UUID> ownerByVenue = new HashMap<>();

		private final Map<UUID, Court> courts = new HashMap<>();

		private List<OperatingPeriod> operatingHours = new ArrayList<>();

		private final List<CourtBlock> blocks = new ArrayList<>();

		private boolean bookingOverlap;

		@Override
		public Venue createVenue(Venue venue, UUID ownerId) {
			venues.put(venue.id(), venue);
			ownerByVenue.put(venue.id(), ownerId);
			return venue;
		}

		@Override
		public Optional<Venue> findVenue(UUID venueId) {
			return Optional.ofNullable(venues.get(venueId));
		}

		@Override
		public Venue saveVenue(Venue venue) {
			venues.put(venue.id(), venue);
			return venue;
		}

		@Override
		public boolean isVenueOwner(UUID venueId, UUID userId) {
			return userId.equals(ownerByVenue.get(venueId));
		}

		@Override
		public Court createCourt(Court court) {
			courts.put(court.id(), court);
			return court;
		}

		@Override
		public Optional<Court> findCourt(UUID courtId) {
			return Optional.ofNullable(courts.get(courtId));
		}

		@Override
		public Court saveCourt(Court court) {
			courts.put(court.id(), court);
			return court;
		}

		@Override
		public void replaceOperatingHours(UUID venueId, List<OperatingPeriod> periods) {
			operatingHours = List.copyOf(periods);
		}

		@Override
		public boolean hasActiveBookingOverlap(UUID courtId, Instant startsAt, Instant endsAt) {
			return bookingOverlap;
		}

		@Override
		public CourtBlock createCourtBlock(CourtBlock block) {
			blocks.add(block);
			return block;
		}

		private Venue givenVenue(UUID ownerId) {
			Venue venue = new Venue(
					UUID.randomUUID(), "Venue", null, null, "Address", null, "District 1",
					"Ho Chi Minh City", new Venue.Coordinates(10.78, 106.7), "Asia/Ho_Chi_Minh", VenueStatus.DRAFT
			);
			createVenue(venue, ownerId);
			return venue;
		}

		private Court givenCourt(UUID ownerId) {
			Venue venue = givenVenue(ownerId);
			Court court = new Court(UUID.randomUUID(), venue.id(), "Court 1", null, CourtStatus.ACTIVE);
			createCourt(court);
			return court;
		}
	}
}
