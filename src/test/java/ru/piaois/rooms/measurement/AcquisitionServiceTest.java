package ru.piaois.rooms.measurement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AcquisitionServiceTest {
    private final AcquisitionService service;

    AcquisitionServiceTest() throws java.io.IOException {
        service = new AcquisitionService(new CsvMeasurementSource(
                java.nio.file.Files.newBufferedReader(java.nio.file.Path.of("src/test/resources/occupancy-arithmetic.csv"))), "occupancy.csv");
    }

    @AfterEach
    void close() { service.close(); }

    @Test
    void completePipelinePreservesSamplingGridAndSmoothsQuantizedValues() {
        service.configure(new AcquisitionSettings(100, 1, 3, 3));
        var result = service.readNext(5);
        assertEquals(5, result.generated());
        assertEquals(3, result.retained());
        assertEquals(2, result.overwritten());
        assertEquals(List.of(3L, 4L, 5L), result.measurements().stream().map(Measurement::sequence).toList());
        double[] quantized = {10, 20, 30, 40, 50};
        for (int i = 0; i < 3; i++) {
            var point = result.measurements().get(i);
            assertEquals(1, point.roomId());
            assertEquals(i + 2, point.elapsedSeconds());
            assertEquals((i + 2) * 1000, Duration.between(result.startedAt(), point.measuredAt()).toMillis());
            assertEquals(quantized[i + 2], point.rawValue());
            assertEquals((quantized[i] + quantized[i + 1] + quantized[i + 2]) / 3, point.filteredValue(), 1e-10);
        }
    }

    @Test
    void resetClearsFilterHistoryAndReplaysSameValues() {
        var first = service.readNext(5).measurements();
        service.readNext(15);
        service.reset();
        var second = service.readNext(5).measurements();
        assertEquals(first.stream().map(Measurement::rawValue).toList(), second.stream().map(Measurement::rawValue).toList());
        assertEquals(first.stream().map(Measurement::filteredValue).toList(), second.stream().map(Measurement::filteredValue).toList());
        assertEquals(1, second.getFirst().sequence());
        assertEquals(10, second.getFirst().filteredValue());
    }

    @Test
    void changingSettingsStartsCleanSeriesAndRejectsInvalidBatch() {
        service.readNext(10);
        var changed = service.configure(new AcquisitionSettings(100, 0.5, 8, 2));
        assertEquals(0, changed.generated());
        assertEquals(java.time.Instant.parse("2026-10-15T09:00:00Z"), changed.startedAt());
        assertTrue(changed.measurements().isEmpty());
        assertEquals(10, service.readNext(1).measurements().getFirst().filteredValue());
        assertThrows(IllegalArgumentException.class, () -> service.readNext(0));
        assertThrows(IllegalArgumentException.class, () -> service.readNext(10001));
    }

    @Test
    void scheduledSamplingStopsAndCanResumeWithoutDuplicateSequence() throws InterruptedException {
        service.configure(new AcquisitionSettings(100, 1, 30, 5));
        service.start();
        assertThrows(IllegalStateException.class, () -> service.readNext(1));
        assertThrows(IllegalStateException.class, service::reset);
        assertThrows(IllegalStateException.class, () -> service.configure(AcquisitionSettings.defaults()));
        awaitSamples(3);
        long before = service.stop().generated();
        Thread.sleep(180);
        assertEquals(before, service.snapshot().generated());
        service.start();
        service.start();
        awaitSamples(before + 2);
        var result = service.stop();
        assertEquals(result.generated(), result.measurements().getLast().sequence());
        for (int i = 1; i < result.measurements().size(); i++) {
            var previous = result.measurements().get(i - 1);
            var current = result.measurements().get(i);
            assertEquals(previous.sequence() + 1, current.sequence());
            assertEquals(1000, Duration.between(previous.measuredAt(), current.measuredAt()).toMillis());
        }
    }

    @Test
    void concurrentReadersAndWritersReceiveConsistentSnapshots() throws Exception {
        try (var workers = Executors.newFixedThreadPool(4)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 4; i++) {
                futures.add(workers.submit(() -> {
                    for (int j = 0; j < 50; j++) {
                        service.readNext(1);
                        var snapshot = service.snapshot();
                        assertEquals(snapshot.retained(), snapshot.measurements().size());
                        assertEquals(snapshot.generated(), snapshot.overwritten() + snapshot.retained());
                    }
                }));
            }
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
        }
        assertEquals(60, service.snapshot().generated());
        assertEquals(10, service.snapshot().retained());
    }

    @Test
    void endOfFileStopsTimerAndDoesNotInventOrRepeatRows() throws InterruptedException {
        service.configure(new AcquisitionSettings(100, 1, 30, 5));
        service.readNext(59);
        service.start();
        awaitSamples(60);
        var end = service.snapshot();
        assertTrue(end.completed());
        assertFalse(end.running());
        assertEquals(60, service.readNext(20).generated());
        assertThrows(IllegalStateException.class, service::start);
        service.reset();
        assertFalse(service.snapshot().completed());
        assertEquals(1, service.readNext(1).generated());
    }

    private void awaitSamples(long count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (service.snapshot().generated() < count && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(service.snapshot().generated() >= count, "Таймер должен получать отсчёты");
    }
}
