package ru.piaois.rooms.measurement;

import java.time.Instant;

/** measuredAt сохранено из CSV; rawValue уже квантовано. */
public record Measurement(long sequence, long roomId, Instant measuredAt, double elapsedSeconds,
                          double sourceValue, double rawValue, double filteredValue) {}
