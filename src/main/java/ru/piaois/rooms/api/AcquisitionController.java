package ru.piaois.rooms.api;

import org.springframework.web.bind.annotation.*;
import ru.piaois.rooms.measurement.AcquisitionService;
import ru.piaois.rooms.measurement.AcquisitionSettings;

@RestController
@RequestMapping("/api/acquisition")
public class AcquisitionController {
    private final AcquisitionService service;

    public AcquisitionController(AcquisitionService service) { this.service = service; }

    @GetMapping
    public AcquisitionService.Snapshot snapshot() { return service.snapshot(); }

    @PutMapping("/settings")
    public AcquisitionService.Snapshot configure(@RequestBody AcquisitionSettings settings) {
        return service.configure(settings);
    }

    @PostMapping("/start")
    public AcquisitionService.Snapshot start() { return service.start(); }

    @PostMapping("/stop")
    public AcquisitionService.Snapshot stop() { return service.stop(); }

    @PostMapping("/reset")
    public AcquisitionService.Snapshot reset() { return service.reset(); }

    @PostMapping("/samples")
    public AcquisitionService.Snapshot readNext(@RequestParam(defaultValue = "20") int count) {
        return service.readNext(count);
    }
}
