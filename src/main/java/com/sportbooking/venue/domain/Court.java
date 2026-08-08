package com.sportbooking.venue.domain;

import java.util.UUID;

public record Court(UUID id, UUID venueId, String name, String description, CourtStatus status) {
}
