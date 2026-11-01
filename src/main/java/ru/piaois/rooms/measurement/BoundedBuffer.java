package ru.piaois.rooms.measurement;

import java.util.ArrayDeque;
import java.util.List;

/** Используется под блокировкой сервиса, отдельно синхронизация не выполняется. */
public final class BoundedBuffer<T> {
    private final int capacity;
    private final ArrayDeque<T> items = new ArrayDeque<>();

    public BoundedBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Размер буфера должен быть положительным");
        this.capacity = capacity;
    }

    public void add(T item) {
        java.util.Objects.requireNonNull(item);
        if (items.size() == capacity) items.removeFirst();
        items.addLast(item);
    }

    public List<T> snapshot() {
        return List.copyOf(items);
    }
}
