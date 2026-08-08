package com.sportbooking.venue.api;

import java.net.URI;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.sportbooking.venue.application.VenueManagementService;
import com.sportbooking.venue.application.VenueManagementService.CreateCourtBlockCommand;
import com.sportbooking.venue.application.VenueManagementService.CreateCourtCommand;
import com.sportbooking.venue.application.VenueManagementService.CreateVenueCommand;
import com.sportbooking.venue.application.VenueManagementService.OperatingPeriodCommand;
import com.sportbooking.venue.application.VenueManagementService.UpdateCourtCommand;
import com.sportbooking.venue.application.VenueManagementService.UpdateVenueCommand;
import com.sportbooking.venue.domain.Court;
import com.sportbooking.venue.domain.CourtBlock;
import com.sportbooking.venue.domain.CourtStatus;
import com.sportbooking.venue.domain.OperatingPeriod;
import com.sportbooking.venue.domain.Venue;
import com.sportbooking.venue.domain.VenueStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class VenueManagementController {

	private final VenueManagementService service;

	VenueManagementController(VenueManagementService service) {
		this.service = service;
	}

	@PostMapping("/venues")
	@PreAuthorize("hasRole('OWNER')")
	ResponseEntity<Venue> createVenue(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateVenueRequest request
	) {
		Venue venue = service.createVenue(userId(jwt), new CreateVenueCommand(
				request.name(), request.description(), request.phoneNumber(), request.addressLine(),
				request.ward(), request.district(), request.location().latitude(), request.location().longitude()
		));
		return ResponseEntity.created(URI.create("/api/v1/venues/" + venue.id())).body(venue);
	}

	@PatchMapping("/venues/{venueId}")
	@PreAuthorize("hasRole('OWNER')")
	Venue updateVenue(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID venueId,
			@Valid @RequestBody UpdateVenueRequest request
	) {
		return service.updateVenue(
				userId(jwt), venueId,
				new UpdateVenueCommand(request.name(), request.description(), request.phoneNumber(), request.status())
		);
	}

	@PostMapping("/venues/{venueId}/courts")
	@PreAuthorize("hasRole('OWNER')")
	ResponseEntity<Court> createCourt(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID venueId,
			@Valid @RequestBody CreateCourtRequest request
	) {
		Court court = service.createCourt(
				userId(jwt), venueId, new CreateCourtCommand(request.name(), request.description())
		);
		return ResponseEntity.created(URI.create("/api/v1/courts/" + court.id())).body(court);
	}

	@PatchMapping("/courts/{courtId}")
	@PreAuthorize("hasRole('OWNER')")
	Court updateCourt(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID courtId,
			@Valid @RequestBody UpdateCourtRequest request
	) {
		return service.updateCourt(
				userId(jwt), courtId,
				new UpdateCourtCommand(request.name(), request.description(), request.status())
		);
	}

	@PutMapping("/venues/{venueId}/operating-hours")
	@PreAuthorize("hasRole('OWNER')")
	VenueOperatingHours replaceOperatingHours(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID venueId,
			@Valid @RequestBody ReplaceOperatingHoursRequest request
	) {
		List<OperatingPeriod> periods = service.replaceOperatingHours(
				userId(jwt),
				venueId,
				request.periods().stream()
						.map(period -> new OperatingPeriodCommand(
								period.dayOfWeek(), period.opensAt(), period.closesAt()
						))
						.toList()
		);
		return new VenueOperatingHours(venueId, periods);
	}

	@PostMapping("/courts/{courtId}/blocks")
	@PreAuthorize("hasRole('OWNER')")
	ResponseEntity<CourtBlock> createCourtBlock(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID courtId,
			@Valid @RequestBody CreateCourtBlockRequest request
	) {
		CourtBlock block = service.createCourtBlock(
				userId(jwt), courtId,
				new CreateCourtBlockCommand(request.startsAt(), request.endsAt(), request.reason())
		);
		return ResponseEntity.created(URI.create("/api/v1/courts/" + courtId + "/blocks/" + block.id()))
				.body(block);
	}

	private UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	record CoordinatesRequest(
			@NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
			@NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
	) {
	}

	record CreateVenueRequest(
			@NotBlank @Size(max = 160) String name,
			@Size(max = 2000) String description,
			@Size(max = 30) String phoneNumber,
			@NotBlank @Size(max = 255) String addressLine,
			@Size(max = 120) String ward,
			@NotBlank @Size(max = 120) String district,
			@NotNull @Valid CoordinatesRequest location
	) {
	}

	record UpdateVenueRequest(
			@Size(max = 160) String name,
			@Size(max = 2000) String description,
			@Size(max = 30) String phoneNumber,
			VenueStatus status
	) {
	}

	record CreateCourtRequest(@NotBlank @Size(max = 100) String name, @Size(max = 1000) String description) {
	}

	record UpdateCourtRequest(
			@Size(max = 100) String name,
			@Size(max = 1000) String description,
			CourtStatus status
	) {
	}

	record OperatingPeriodRequest(
			@Min(1) @Max(7) int dayOfWeek,
			@NotNull LocalTime opensAt,
			@NotNull LocalTime closesAt
	) {
	}

	record ReplaceOperatingHoursRequest(@NotNull List<@Valid OperatingPeriodRequest> periods) {
	}

	record VenueOperatingHours(UUID venueId, List<OperatingPeriod> periods) {
	}

	record CreateCourtBlockRequest(
			@NotNull Instant startsAt,
			@NotNull Instant endsAt,
			@Size(max = 255) String reason
	) {
	}
}
