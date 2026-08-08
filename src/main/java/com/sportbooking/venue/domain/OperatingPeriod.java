package com.sportbooking.venue.domain;

import java.time.LocalTime;

public record OperatingPeriod(int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
}
