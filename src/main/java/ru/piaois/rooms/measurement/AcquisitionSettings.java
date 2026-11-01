package ru.piaois.rooms.measurement;

public record AcquisitionSettings(long samplePeriodMs, double quantumPercent,
                                  int bufferCapacity, int averagingWindow) {
    public AcquisitionSettings {
        if (samplePeriodMs < 100 || samplePeriodMs > 5000) {
            throw new IllegalArgumentException("Период чтения должен быть от 100 до 5000 мс");
        }
        // Разрешены шаги, делящие диапазон 0..100 на целое число интервалов.
        if (!Double.isFinite(quantumPercent) || quantumPercent < 0.1 || quantumPercent > 10
                || Math.abs(100 / quantumPercent - Math.rint(100 / quantumPercent)) > 1e-8) {
            throw new IllegalArgumentException("Шаг квантования должен быть от 0,1 до 10 и делить 100 без остатка");
        }
        if (bufferCapacity < 1 || bufferCapacity > 1000) {
            throw new IllegalArgumentException("Размер буфера должен быть от 1 до 1000");
        }
        if (averagingWindow < 1 || averagingWindow > bufferCapacity) {
            throw new IllegalArgumentException("Окно среднего должно быть от 1 до размера буфера");
        }
    }

    public static AcquisitionSettings defaults() {
        return new AcquisitionSettings(1000, 1, 10, 3);
    }
}
