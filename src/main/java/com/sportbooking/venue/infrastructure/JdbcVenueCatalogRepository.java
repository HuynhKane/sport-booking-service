package com.sportbooking.venue.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sportbooking.venue.application.VenueCatalogRepository;
import com.sportbooking.venue.application.VenueConflictException;
import com.sportbooking.venue.domain.Court;
import com.sportbooking.venue.domain.CourtBlock;
import com.sportbooking.venue.domain.CourtStatus;
import com.sportbooking.venue.domain.OperatingPeriod;
import com.sportbooking.venue.domain.Venue;
import com.sportbooking.venue.domain.VenueStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcVenueCatalogRepository implements VenueCatalogRepository {

	private final JdbcTemplate jdbcTemplate;

	JdbcVenueCatalogRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public Venue createVenue(Venue venue, UUID ownerId) {
		jdbcTemplate.update(
				"""
				INSERT INTO venue (
				    id, name, description, phone_number, address_line, ward, district,
				    city, location, timezone, status
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?,
				    ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?)
				""",
				venue.id(), venue.name(), venue.description(), venue.phoneNumber(), venue.addressLine(),
				venue.ward(), venue.district(), venue.city(), venue.location().longitude(),
				venue.location().latitude(), venue.timezone(), venue.status().name()
		);
		jdbcTemplate.update(
				"INSERT INTO venue_owner (venue_id, user_id) VALUES (?, ?)", venue.id(), ownerId
		);
		return venue;
	}

	@Override
	public Optional<Venue> findVenue(UUID venueId) {
		List<Venue> venues = jdbcTemplate.query(
				"""
				SELECT id, name, description, phone_number, address_line, ward, district, city,
				       ST_Y(location::geometry) AS latitude,
				       ST_X(location::geometry) AS longitude,
				       timezone, status
				FROM venue
				WHERE id = ?
				""",
				this::mapVenue,
				venueId
		);
		return venues.stream().findFirst();
	}

	@Override
	public Venue saveVenue(Venue venue) {
		jdbcTemplate.update(
				"""
				UPDATE venue
				SET name = ?, description = ?, phone_number = ?, status = ?, updated_at = now()
				WHERE id = ?
				""",
				venue.name(), venue.description(), venue.phoneNumber(), venue.status().name(), venue.id()
		);
		return venue;
	}

	@Override
	public boolean isVenueOwner(UUID venueId, UUID userId) {
		Boolean owned = jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM venue_owner WHERE venue_id = ? AND user_id = ?)",
				Boolean.class,
				venueId,
				userId
		);
		return Boolean.TRUE.equals(owned);
	}

	@Override
	public Court createCourt(Court court) {
		try {
			jdbcTemplate.update(
					"""
					INSERT INTO court (id, venue_id, name, description, status)
					VALUES (?, ?, ?, ?, ?)
					""",
					court.id(), court.venueId(), court.name(), court.description(), court.status().name()
			);
			return court;
		} catch (DataIntegrityViolationException exception) {
			throw new VenueConflictException("Court name already exists at this venue");
		}
	}

	@Override
	public Optional<Court> findCourt(UUID courtId) {
		List<Court> courts = jdbcTemplate.query(
				"SELECT id, venue_id, name, description, status FROM court WHERE id = ?",
				this::mapCourt,
				courtId
		);
		return courts.stream().findFirst();
	}

	@Override
	public Court saveCourt(Court court) {
		try {
			jdbcTemplate.update(
					"""
					UPDATE court
					SET name = ?, description = ?, status = ?, updated_at = now()
					WHERE id = ?
					""",
					court.name(), court.description(), court.status().name(), court.id()
			);
			return court;
		} catch (DataIntegrityViolationException exception) {
			throw new VenueConflictException("Court name already exists at this venue");
		}
	}

	@Override
	@Transactional
	public void replaceOperatingHours(UUID venueId, List<OperatingPeriod> periods) {
		jdbcTemplate.update("DELETE FROM venue_operating_hour WHERE venue_id = ?", venueId);
		for (OperatingPeriod period : periods) {
			jdbcTemplate.update(
					"""
					INSERT INTO venue_operating_hour (id, venue_id, day_of_week, opens_at, closes_at)
					VALUES (?, ?, ?, ?, ?)
					""",
					UUID.randomUUID(), venueId, period.dayOfWeek(),
					Time.valueOf(period.opensAt()), Time.valueOf(period.closesAt())
			);
		}
	}

	@Override
	public boolean hasActiveBookingOverlap(UUID courtId, Instant startsAt, Instant endsAt) {
		Boolean overlap = jdbcTemplate.queryForObject(
				"""
				SELECT EXISTS (
				    SELECT 1 FROM booking
				    WHERE court_id = ?
				      AND status IN ('PENDING', 'CONFIRMED')
				      AND tstzrange(starts_at, ends_at, '[)')
				          && tstzrange(?::timestamptz, ?::timestamptz, '[)')
				)
				""",
				Boolean.class,
				courtId,
				Timestamp.from(startsAt),
				Timestamp.from(endsAt)
		);
		return Boolean.TRUE.equals(overlap);
	}

	@Override
	public CourtBlock createCourtBlock(CourtBlock block) {
		jdbcTemplate.update(
				"""
				INSERT INTO court_block (
				    id, court_id, starts_at, ends_at, reason, created_by_user_id, created_at
				) VALUES (?, ?, ?, ?, ?, ?, ?)
				""",
				block.id(), block.courtId(), Timestamp.from(block.startsAt()), Timestamp.from(block.endsAt()),
				block.reason(), block.createdByUserId(), Timestamp.from(block.createdAt())
		);
		return block;
	}

	private Venue mapVenue(ResultSet resultSet, int rowNumber) throws SQLException {
		return new Venue(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("name"),
				resultSet.getString("description"),
				resultSet.getString("phone_number"),
				resultSet.getString("address_line"),
				resultSet.getString("ward"),
				resultSet.getString("district"),
				resultSet.getString("city"),
				new Venue.Coordinates(resultSet.getDouble("latitude"), resultSet.getDouble("longitude")),
				resultSet.getString("timezone"),
				VenueStatus.valueOf(resultSet.getString("status"))
		);
	}

	private Court mapCourt(ResultSet resultSet, int rowNumber) throws SQLException {
		return new Court(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("venue_id", UUID.class),
				resultSet.getString("name"),
				resultSet.getString("description"),
				CourtStatus.valueOf(resultSet.getString("status"))
		);
	}
}
