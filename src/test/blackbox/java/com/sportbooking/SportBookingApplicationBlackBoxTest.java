package com.sportbooking;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SportBookingApplicationBlackBoxTest {

	private static final DockerImageName POSTGIS_IMAGE = DockerImageName
			.parse("imresamu/postgis:17-3.5-bookworm")
			.asCompatibleSubstituteFor("postgres");

	@Container
	@ServiceConnection
	static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(POSTGIS_IMAGE)
			.withDatabaseName("sport_booking")
			.withUsername("sport_booking")
			.withPassword("sport_booking");

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void reportsApplicationHealthThroughHttp() throws Exception {
		HttpResponse<String> response = get("/actuator/health");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"");
		assertThat(response.body()).doesNotContain("database", "password", "details");
	}

	@Test
	void reportsReadinessThroughHttp() throws Exception {
		HttpResponse<String> response = get("/actuator/health/readiness");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"");
	}

	@Test
	void identifiesApplicationAndCommitThroughHttp() throws Exception {
		HttpResponse<String> response = get("/actuator/info");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"name\":\"sport-booking-service\"");
		assertThat(response.body()).contains("\"commit\":\"local\"");
	}

	@Test
	void registersAccountAndAuthenticatesWithBearerToken() throws Exception {
		String email = "player-" + UUID.randomUUID() + "@example.com";
		HttpResponse<String> registration = post("/api/v1/accounts", """
				{"email":"%s","password":"password123","displayName":"Player","role":"PLAYER"}
				""".formatted(email));

		assertThat(registration.statusCode()).isEqualTo(201);
		assertThat(registration.body()).contains("\"email\":\"" + email + "\"");
		assertThat(registration.body()).doesNotContain("password", "passwordHash");

		HttpResponse<String> authentication = post("/api/v1/auth/tokens", """
				{"email":"%s","password":"password123"}
				""".formatted(email));

		assertThat(authentication.statusCode()).isEqualTo(200);
		assertThat(authentication.body()).contains("\"tokenType\":\"Bearer\"");
		assertThat(authentication.body()).contains("\"expiresInSeconds\":3600");
		assertThat(authentication.body()).contains("\"accessToken\":");
	}

	@Test
	void rejectsCaseInsensitiveDuplicateWithoutSensitiveData() throws Exception {
		String email = "duplicate-" + UUID.randomUUID() + "@example.com";
		post("/api/v1/accounts", registrationBody(email));

		HttpResponse<String> duplicate = post("/api/v1/accounts", registrationBody(email.toUpperCase()));

		assertThat(duplicate.statusCode()).isEqualTo(409);
		assertThat(duplicate.body()).doesNotContain(email, "password", "passwordHash");
	}

	@Test
	void usesGenericUnauthorizedResponseForInvalidOrDisabledAccount() throws Exception {
		HttpResponse<String> unknown = post("/api/v1/auth/tokens", """
				{"email":"unknown@example.com","password":"wrong-password"}
				""");
		assertThat(unknown.statusCode()).isEqualTo(401);
		assertThat(unknown.body()).contains("Invalid email or password");

		String email = "disabled-" + UUID.randomUUID() + "@example.com";
		post("/api/v1/accounts", registrationBody(email));
		jdbcTemplate.update("UPDATE app_user SET status = 'DISABLED' WHERE email = ?", email);

		HttpResponse<String> disabled = post("/api/v1/auth/tokens", """
				{"email":"%s","password":"password123"}
				""".formatted(email));
		assertThat(disabled.statusCode()).isEqualTo(401);
		assertThat(disabled.body()).contains("Invalid email or password");
	}

	@Test
	void keepsDiscoveryPublicAndProtectsBookingOperations() throws Exception {
		HttpResponse<String> discovery = get("/api/v1/venues");
		HttpResponse<String> booking = post("/api/v1/bookings", "{}");

		assertThat(discovery.statusCode()).isNotEqualTo(401);
		assertThat(booking.statusCode()).isEqualTo(401);
	}

	@Test
	void ownerCreatesDraftVenueWhilePlayerIsForbidden() throws Exception {
		AuthenticatedAccount owner = registerAndAuthenticate("OWNER");
		AuthenticatedAccount player = registerAndAuthenticate("PLAYER");

		HttpResponse<String> created = authorizedRequest(
				"POST", "/api/v1/venues", venueBody(), owner.accessToken()
		);
		HttpResponse<String> forbidden = authorizedRequest(
				"POST", "/api/v1/venues", venueBody(), player.accessToken()
		);
		HttpResponse<String> invalid = authorizedRequest(
				"POST", "/api/v1/venues", """
				{"name":"Invalid","addressLine":"Address","district":"District 1",
				 "location":{"latitude":91,"longitude":106.7}}
				""", owner.accessToken()
		);

		assertThat(created.statusCode()).isEqualTo(201);
		assertThat(created.body()).contains(
				"\"name\":\"Pars Badminton\"",
				"\"city\":\"Ho Chi Minh City\"",
				"\"timezone\":\"Asia/Ho_Chi_Minh\"",
				"\"status\":\"DRAFT\""
		);
		assertThat(forbidden.statusCode()).isEqualTo(403);
		assertThat(invalid.statusCode()).isEqualTo(400);
	}

	@Test
	void onlyAssociatedOwnerUpdatesVenueAndManagesCourts() throws Exception {
		AuthenticatedAccount owner = registerAndAuthenticate("OWNER");
		AuthenticatedAccount unrelatedOwner = registerAndAuthenticate("OWNER");
		UUID venueId = createVenue(owner);

		HttpResponse<String> updated = authorizedRequest(
				"PATCH", "/api/v1/venues/" + venueId,
				"{\"name\":\"Pars Arena\",\"status\":\"ACTIVE\"}", owner.accessToken()
		);
		HttpResponse<String> forbidden = authorizedRequest(
				"PATCH", "/api/v1/venues/" + venueId,
				"{\"status\":\"INACTIVE\"}", unrelatedOwner.accessToken()
		);
		HttpResponse<String> court = authorizedRequest(
				"POST", "/api/v1/venues/" + venueId + "/courts",
				"{\"name\":\"Court 1\",\"description\":\"Wood floor\"}", owner.accessToken()
		);
		UUID courtId = UUID.fromString(jsonValue(court.body(), "id"));
		HttpResponse<String> updatedCourt = authorizedRequest(
				"PATCH", "/api/v1/courts/" + courtId,
				"{\"name\":\"Court A\",\"status\":\"MAINTENANCE\"}", owner.accessToken()
		);
		HttpResponse<String> duplicate = authorizedRequest(
				"POST", "/api/v1/venues/" + venueId + "/courts",
				"{\"name\":\"Court A\"}", owner.accessToken()
		);

		assertThat(updated.statusCode()).isEqualTo(200);
		assertThat(updated.body()).contains("\"name\":\"Pars Arena\"", "\"status\":\"ACTIVE\"");
		assertThat(forbidden.statusCode()).isEqualTo(403);
		assertThat(court.statusCode()).isEqualTo(201);
		assertThat(court.body()).contains("\"status\":\"ACTIVE\"");
		assertThat(updatedCourt.statusCode()).isEqualTo(200);
		assertThat(updatedCourt.body()).contains("\"name\":\"Court A\"", "\"status\":\"MAINTENANCE\"");
		assertThat(duplicate.statusCode()).isEqualTo(409);
	}

	@Test
	void replacesCompleteWeeklyScheduleAndPreservesItWhenReplacementIsInvalid() throws Exception {
		AuthenticatedAccount owner = registerAndAuthenticate("OWNER");
		UUID venueId = createVenue(owner);
		String validSchedule = """
				{"periods":[
				  {"dayOfWeek":1,"opensAt":"06:00:00","closesAt":"12:00:00"},
				  {"dayOfWeek":1,"opensAt":"14:00:00","closesAt":"22:00:00"}
				]}
				""";
		String overlappingSchedule = """
				{"periods":[
				  {"dayOfWeek":1,"opensAt":"06:00:00","closesAt":"12:00:00"},
				  {"dayOfWeek":1,"opensAt":"11:00:00","closesAt":"18:00:00"}
				]}
				""";

		HttpResponse<String> replaced = authorizedRequest(
				"PUT", "/api/v1/venues/" + venueId + "/operating-hours", validSchedule, owner.accessToken()
		);
		HttpResponse<String> invalid = authorizedRequest(
				"PUT", "/api/v1/venues/" + venueId + "/operating-hours", overlappingSchedule, owner.accessToken()
		);
		Integer savedPeriods = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM venue_operating_hour WHERE venue_id = ?", Integer.class, venueId
		);

		assertThat(replaced.statusCode()).isEqualTo(200);
		assertThat(replaced.body()).contains("\"periods\":[", "\"dayOfWeek\":1");
		assertThat(invalid.statusCode()).isEqualTo(400);
		assertThat(savedPeriods).isEqualTo(2);
	}

	@Test
	void rejectsCourtBlockThatOverlapsActiveBooking() throws Exception {
		AuthenticatedAccount owner = registerAndAuthenticate("OWNER");
		UUID venueId = createVenue(owner);
		HttpResponse<String> courtResponse = authorizedRequest(
				"POST", "/api/v1/venues/" + venueId + "/courts", "{\"name\":\"Court 1\"}", owner.accessToken()
		);
		UUID courtId = UUID.fromString(jsonValue(courtResponse.body(), "id"));
		jdbcTemplate.update(
				"""
				INSERT INTO booking (
				    court_id, player_user_id, created_by_user_id, starts_at, ends_at, status, source
				) VALUES (?, ?, ?, '2030-01-01T10:00:00Z', '2030-01-01T11:00:00Z', 'CONFIRMED', 'OWNER')
				""",
				courtId, owner.accountId(), owner.accountId()
		);

		HttpResponse<String> conflict = authorizedRequest(
				"POST", "/api/v1/courts/" + courtId + "/blocks",
				"""
				{"startsAt":"2030-01-01T10:30:00Z","endsAt":"2030-01-01T11:30:00Z","reason":"Maintenance"}
				""",
				owner.accessToken()
		);
		HttpResponse<String> created = authorizedRequest(
				"POST", "/api/v1/courts/" + courtId + "/blocks",
				"""
				{"startsAt":"2030-01-01T11:00:00Z","endsAt":"2030-01-01T12:00:00Z","reason":"Maintenance"}
				""",
				owner.accessToken()
		);

		assertThat(conflict.statusCode()).isEqualTo(409);
		assertThat(created.statusCode()).isEqualTo(201);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM court_block WHERE court_id = ?", Integer.class, courtId
		)).isEqualTo(1);
	}

	private HttpResponse<String> get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + path))
				.GET()
				.build();

		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> post(String path, String body) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();

		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> authorizedRequest(String method, String path, String body, String token)
			throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + path))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + token)
				.method(method, HttpRequest.BodyPublishers.ofString(body))
				.build();
		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
	}

	private AuthenticatedAccount registerAndAuthenticate(String role) throws Exception {
		String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
		HttpResponse<String> registration = post("/api/v1/accounts", """
				{"email":"%s","password":"password123","displayName":"Test User","role":"%s"}
				""".formatted(email, role));
		HttpResponse<String> authentication = post("/api/v1/auth/tokens", """
				{"email":"%s","password":"password123"}
				""".formatted(email));
		return new AuthenticatedAccount(
				UUID.fromString(jsonValue(registration.body(), "id")),
				jsonValue(authentication.body(), "accessToken")
		);
	}

	private UUID createVenue(AuthenticatedAccount owner) throws Exception {
		HttpResponse<String> response = authorizedRequest("POST", "/api/v1/venues", venueBody(), owner.accessToken());
		return UUID.fromString(jsonValue(response.body(), "id"));
	}

	private String venueBody() {
		return """
				{
				  "name":"Pars Badminton",
				  "addressLine":"1 Nguyen Hue",
				  "district":"District 1",
				  "location":{"latitude":10.78,"longitude":106.7}
				}
				""";
	}

	private String jsonValue(String json, String field) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("\\\"" + field + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
				.matcher(json);
		if (!matcher.find()) {
			throw new IllegalArgumentException("Missing JSON field " + field + " in " + json);
		}
		return matcher.group(1);
	}

	private String registrationBody(String email) {
		return """
				{"email":"%s","password":"password123","displayName":"Player","role":"PLAYER"}
				""".formatted(email);
	}

	private record AuthenticatedAccount(UUID accountId, String accessToken) {
	}

}
