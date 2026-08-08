package com.sportbooking.venue.domain;

import java.time.Instant;
import java.util.UUID;

public record CourtBlock(
		UUID id,
		UUID courtId,
		Instant startsAt,
		Instant endsAt,
		String reason,
		UUID createdByUserId,
		Instant createdAt
) {
}
