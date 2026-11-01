package ru.piaois.rooms.measurement;

public final class Quantizer {
    private final double step;

    public Quantizer(double step) {
        if (!Double.isFinite(step) || step <= 0 || step > 100) {
            throw new IllegalArgumentException("Шаг квантования должен быть от 0 до 100, исключая 0");
        }
        this.step = step;
    }

    public double apply(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 100) {
            throw new IllegalArgumentException("Значение источника должно быть от 0 до 100");
        }
        return Math.clamp(Math.round(value / step) * step, 0, 100);
    }
}
