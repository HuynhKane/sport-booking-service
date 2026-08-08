package com.sportbooking.venue.infrastructure;

import java.time.Clock;

import com.sportbooking.venue.application.VenueCatalogRepository;
import com.sportbooking.venue.application.VenueManagementService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class VenueConfiguration {

	@Bean
	VenueManagementService venueManagementService(VenueCatalogRepository repository, Clock clock) {
		return new VenueManagementService(repository, clock);
	}
}
