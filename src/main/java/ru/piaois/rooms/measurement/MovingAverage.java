package ru.piaois.rooms.measurement;

import java.util.ArrayDeque;

public final class MovingAverage {
    private final int window;
    private final ArrayDeque<Double> values = new ArrayDeque<>();
    private double sum;

    public MovingAverage(int window) {
        if (window < 1) throw new IllegalArgumentException("Окно среднего должно быть положительным");
        this.window = window;
    }

    public double accept(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Значение должно быть конечным");
        if (values.size() == window) sum -= values.removeFirst();
        values.addLast(value);
        sum += value;
        // При заполнении окна делим на число уже полученных отсчётов, без добавления нулей.
        return sum / values.size();
    }
}
