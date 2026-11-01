package ru.piaois.rooms.measurement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Читает конечную учебную серию одной аудитории из CSV, без генерации значений. */
public final class CsvMeasurementSource {
    private final List<SourceMeasurement> records;

    public CsvMeasurementSource(Reader reader) throws IOException {
        List<SourceMeasurement> loaded = new ArrayList<>();
        try (BufferedReader input = new BufferedReader(reader)) {
            String header = input.readLine();
            if (header == null || !header.replace("\uFEFF", "").equals("measuredAt,roomId,occupancyPercent")) {
                throw new IllegalArgumentException("CSV должен начинаться с measuredAt,roomId,occupancyPercent");
            }
            String line;
            int lineNumber = 1;
            while ((line = input.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                if (loaded.size() >= 100000) throw new IllegalArgumentException("CSV должен содержать не более 100000 записей");
                String[] fields = line.split(",", -1);
                if (fields.length != 3) throw invalid(lineNumber, "нужны три поля через запятую");
                try {
                    Instant time = Instant.parse(fields[0].trim());
                    long roomId = Long.parseLong(fields[1].trim());
                    double value = Double.parseDouble(fields[2].trim());
                    if (roomId <= 0) throw invalid(lineNumber, "ID аудитории должен быть положительным");
                    if (!Double.isFinite(value) || value < 0 || value > 100) {
                        throw invalid(lineNumber, "занятость должна быть от 0 до 100");
                    }
                    if (!loaded.isEmpty()) {
                        var previous = loaded.getLast();
                        if (roomId != previous.roomId()) throw invalid(lineNumber, "в одной серии должна быть одна аудитория");
                        if (!time.isAfter(previous.measuredAt())) throw invalid(lineNumber, "время должно строго возрастать");
                    }
                    loaded.add(new SourceMeasurement(time, roomId, value));
                } catch (NumberFormatException | DateTimeParseException ex) {
                    throw invalid(lineNumber, "неверный формат времени или числа");
                }
            }
        }
        if (loaded.isEmpty()) throw new IllegalArgumentException("CSV не содержит записей");
        records = List.copyOf(loaded);
    }

    public int size() { return records.size(); }
    public SourceMeasurement read(int index) { return records.get(index); }

    private static IllegalArgumentException invalid(int line, String reason) {
        return new IllegalArgumentException("CSV, строка " + line + ": " + reason);
    }
}
