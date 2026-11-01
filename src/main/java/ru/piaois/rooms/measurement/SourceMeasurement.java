package ru.piaois.rooms.measurement;

import java.time.Instant;

public record SourceMeasurement(Instant measuredAt, long roomId, double occupancyPercent) {}
