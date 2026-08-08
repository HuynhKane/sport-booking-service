package com.sportbooking;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatabaseMigrationIntegrationTest {

	private static final DockerImageName POSTGIS_IMAGE = DockerImageName
			.parse("imresamu/postgis:17-3.5-bookworm")
			.asCompatibleSubstituteFor("postgres");

	@Container
	@ServiceConnection
	static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(POSTGIS_IMAGE)
			.withDatabaseName("sport_booking")
			.withUsername("sport_booking")
			.withPassword("sport_booking");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void appliesEveryFlywayMigrationAndEnablesRequiredExtensions() {
		Integer migrationCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE success",
				Integer.class
		);
		Integer extensionCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM pg_extension WHERE extname IN ('postgis', 'btree_gist')",
				Integer.class
		);

		assertThat(migrationCount).isEqualTo(5);
		assertThat(extensionCount).isEqualTo(2);
	}

	@Test
	void rejectsOverlappingActiveBookingsForTheSameCourt() {
		UUID userId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		UUID courtId = UUID.randomUUID();

		jdbcTemplate.update(
				"INSERT INTO app_user (id, email, password_hash, display_name) VALUES (?, ?, ?, ?)",
				userId,
				userId + "@example.com",
				"integration-test-password-hash",
				"Integration Test Player"
		);
		jdbcTemplate.update(
				"""
				INSERT INTO venue (id, name, address_line, district, location, status)
				VALUES (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(106.7, 10.78), 4326)::geography, 'ACTIVE')
				""",
				venueId,
				"Integration Test Venue",
				"Test address",
				"District 1"
		);
		jdbcTemplate.update(
				"INSERT INTO court (id, venue_id, name) VALUES (?, ?, ?)",
				courtId,
				venueId,
				"Court 1"
		);
		insertBooking(courtId, userId, "2030-01-01T10:00:00Z", "2030-01-01T11:00:00Z");

		assertThatThrownBy(() ->
				insertBooking(courtId, userId, "2030-01-01T10:30:00Z", "2030-01-01T11:30:00Z")
		).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void enforcesCourtNameUniquenessWithinOneVenue() {
		UUID venueId = UUID.randomUUID();
		jdbcTemplate.update(
				"""
				INSERT INTO venue (id, name, address_line, district, location)
				VALUES (?, 'Unique Court Venue', 'Test address', 'District 1',
				        ST_SetSRID(ST_MakePoint(106.7, 10.78), 4326)::geography)
				""",
				venueId
		);
		jdbcTemplate.update(
				"INSERT INTO court (id, venue_id, name) VALUES (?, ?, 'Court 1')",
				UUID.randomUUID(), venueId
		);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO court (id, venue_id, name) VALUES (?, ?, 'Court 1')",
				UUID.randomUUID(), venueId
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertBooking(UUID courtId, UUID userId, String startsAt, String endsAt) {
		jdbcTemplate.update(
				"""
				INSERT INTO booking (
				    court_id, player_user_id, created_by_user_id, starts_at, ends_at, status, source
				) VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, 'CONFIRMED', 'PLAYER')
				""",
				courtId,
				userId,
				userId,
				startsAt,
				endsAt
		);
	}

}
