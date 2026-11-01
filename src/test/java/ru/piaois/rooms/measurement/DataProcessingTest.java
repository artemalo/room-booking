package ru.piaois.rooms.measurement;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataProcessingTest {
    @Test
    void movingAverageUsesKnownSequenceAndPartialWindow() {
        MovingAverage average = new MovingAverage(3);
        double[] input = {10, 20, 30, 40, 50};
        double[] expected = {10, 15, 20, 30, 40};
        for (int i = 0; i < input.length; i++) assertEquals(expected[i], average.accept(input[i]), 1e-10);
    }

    @Test
    void windowOnePreservesValuesAndInvalidValueDoesNotPolluteWindow() {
        MovingAverage average = new MovingAverage(1);
        assertEquals(70, average.accept(70));
        assertThrows(IllegalArgumentException.class, () -> average.accept(Double.NaN));
        assertEquals(10, average.accept(10));
    }

    @Test
    void quantizationRoundsToNearestStepIncludingBounds() {
        Quantizer quantizer = new Quantizer(0.5);
        assertEquals(0, quantizer.apply(0));
        assertEquals(52, quantizer.apply(52.24));
        assertEquals(52.5, quantizer.apply(52.25));
        assertEquals(100, quantizer.apply(99.99));
        assertThrows(IllegalArgumentException.class, () -> quantizer.apply(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> quantizer.apply(101));
        assertThrows(IllegalArgumentException.class, () -> new Quantizer(0));
    }

    @Test
    void bufferEvictsOldestAndReturnsIndependentImmutableSnapshot() {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(3);
        buffer.add(1);
        List<Integer> snapshot = buffer.snapshot();
        for (int i = 2; i <= 5; i++) buffer.add(i);
        assertEquals(List.of(3, 4, 5), buffer.snapshot());
        assertEquals(List.of(1), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(9));
    }

    @Test
    void settingsRejectInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new AcquisitionSettings(0, 1, 10, 3));
        assertThrows(IllegalArgumentException.class, () -> new AcquisitionSettings(6000, 1, 10, 3));
        assertThrows(IllegalArgumentException.class, () -> new AcquisitionSettings(100, 3, 10, 3));
        assertThrows(IllegalArgumentException.class, () -> new AcquisitionSettings(100, Double.NaN, 10, 3));
        assertThrows(IllegalArgumentException.class, () -> new AcquisitionSettings(100, 1, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new AcquisitionSettings(100, 1, 10, 11));
    }
}

