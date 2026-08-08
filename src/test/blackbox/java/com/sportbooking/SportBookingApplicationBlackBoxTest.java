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

	private String registrationBody(String email) {
		return """
				{"email":"%s","password":"password123","displayName":"Player","role":"PLAYER"}
				""".formatted(email);
	}

}
