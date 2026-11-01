package ru.piaois.rooms.measurement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CsvMeasurementSourceTest {
    private static final String HEADER = "measuredAt,roomId,occupancyPercent\n";

    @Test
    void readsExactFileValuesAndTimes() throws Exception {
        var source = new CsvMeasurementSource(new StringReader("\uFEFF" + HEADER
                + "2026-10-15T09:00:00Z,7,12.3\n2026-10-15T09:00:02Z,7,89.7\n"));
        assertEquals(2, source.size());
        assertEquals(12.3, source.read(0).occupancyPercent());
        assertEquals(7, source.read(1).roomId());
        assertEquals(Instant.parse("2026-10-15T09:00:02Z"), source.read(1).measuredAt());
        assertEquals(source.read(0), source.read(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "bad-date,1,50", "2026-10-15T09:00:00Z,0,50",
            "2026-10-15T09:00:00Z,1,NaN", "2026-10-15T09:00:00Z,1,101",
            "2026-10-15T09:00:00Z,1,-1", "2026-10-15T09:00:00Z,1,50,extra",
            "2026-10-15T09:00:00Z,1,50\n2026-10-15T09:00:00Z,1,60",
            "2026-10-15T09:00:00Z,1,50\n2026-10-15T08:00:00Z,1,60",
            "2026-10-15T09:00:00Z,1,50\n2026-10-15T09:00:01Z,2,60"
    })
    void rejectsInvalidRecords(String rows) {
        assertThrows(IllegalArgumentException.class, () -> new CsvMeasurementSource(new StringReader(HEADER + rows)));
    }

    @Test
    void rejectsWrongHeader() {
        assertThrows(IllegalArgumentException.class, () -> new CsvMeasurementSource(new StringReader("time,room,value\n")));
    }
}
