package com.sportbooking;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Value;
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

	private HttpResponse<String> get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + path))
				.GET()
				.build();

		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
	}

}
