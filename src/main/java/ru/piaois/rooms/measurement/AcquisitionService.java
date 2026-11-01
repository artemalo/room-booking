package ru.piaois.rooms.measurement;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class AcquisitionService {
    private static final Logger LOG = LoggerFactory.getLogger(AcquisitionService.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("measurement-sampler").factory());
    private final CsvMeasurementSource source;
    private final String sourceName;
    private AcquisitionSettings settings = AcquisitionSettings.defaults();
    private Quantizer quantizer;
    private MovingAverage average;
    private BoundedBuffer<Measurement> buffer;
    private ScheduledFuture<?> task;
    private boolean running;
    private long generation;
    private int generated;
    private String error;

    @Autowired
    public AcquisitionService(@Value("${measurements.source}") Resource resource) throws IOException {
        this(new CsvMeasurementSource(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)), resource.getFilename());
    }

    public AcquisitionService(CsvMeasurementSource source, String sourceName) {
        this.source = java.util.Objects.requireNonNull(source);
        this.sourceName = sourceName;
        resetState();
    }

    public synchronized Snapshot snapshot() {
        List<Measurement> measurements = buffer.snapshot();
        return new Snapshot(running, generated == source.size(), settings, generated, measurements.size(),
                generated - measurements.size(), source.size(), sourceName, source.read(0).roomId(),
                source.read(0).measuredAt(), error, measurements);
    }

    public synchronized Snapshot configure(AcquisitionSettings replacement) {
        requireStopped();
        settings = java.util.Objects.requireNonNull(replacement);
        resetState();
        return snapshot();
    }

    public synchronized Snapshot start() {
        if (generated == source.size()) throw new IllegalStateException("Файл прочитан. Очистите серию, чтобы повторить чтение");
        if (!running) {
            error = null;
            running = true;
            long token = ++generation;
            task = scheduler.scheduleAtFixedRate(() -> tick(token), 0, settings.samplePeriodMs(), TimeUnit.MILLISECONDS);
        }
        return snapshot();
    }

    public synchronized Snapshot stop() {
        running = false;
        generation++;
        if (task != null) task.cancel(false);
        task = null;
        return snapshot();
    }

    public synchronized Snapshot reset() {
        requireStopped();
        resetState();
        return snapshot();
    }

    public synchronized Snapshot readNext(int count) {
        requireStopped();
        if (count < 1 || count > 10000) throw new IllegalArgumentException("Количество записей должно быть от 1 до 10000");
        for (int i = 0; i < count && generated < source.size(); i++) sample();
        return snapshot();
    }

    private synchronized void tick(long token) {
        // Старый ожидающий вызов не должен попасть в новую сессию после stop/start.
        if (!running || token != generation) return;
        try {
            sample();
            if (generated == source.size()) stop();
        } catch (RuntimeException ex) {
            stop();
            error = "Сбор остановлен из-за ошибки обработки";
            LOG.error("Sampling failed", ex);
        }
    }

    private void sample() {
        SourceMeasurement input = source.read(generated);
        double elapsedSeconds = Duration.between(source.read(0).measuredAt(), input.measuredAt()).toMillis() / 1000.0;
        double rawValue = quantizer.apply(input.occupancyPercent());
        double filteredValue = average.accept(rawValue);
        buffer.add(new Measurement(generated + 1L, input.roomId(), input.measuredAt(), elapsedSeconds,
                input.occupancyPercent(), rawValue, filteredValue));
        generated++;
    }

    private void resetState() {
        quantizer = new Quantizer(settings.quantumPercent());
        average = new MovingAverage(settings.averagingWindow());
        buffer = new BoundedBuffer<>(settings.bufferCapacity());
        generated = 0;
        error = null;
    }

    private void requireStopped() {
        if (running) throw new IllegalStateException("Сначала остановите сбор данных");
    }

    @PreDestroy
    public synchronized void close() {
        stop();
        scheduler.shutdownNow();
    }

    public record Snapshot(boolean running, boolean completed, AcquisitionSettings settings, long generated,
                           int retained, long overwritten, int totalRecords, String sourceName, long roomId,
                           Instant startedAt, String error, List<Measurement> measurements) {}
}
