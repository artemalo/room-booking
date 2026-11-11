package ru.piaois.rooms.api;

import org.springframework.web.bind.annotation.*;
import ru.piaois.rooms.measurement.AcquisitionService;
import ru.piaois.rooms.measurement.AcquisitionSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/acquisition")
@Tag(name = "Измерения", description = "Чтение учебного CSV и состояние источника. Эти методы не требуют входа.")
public class AcquisitionController {
    private final AcquisitionService service;

    public AcquisitionController(AcquisitionService service) { this.service = service; }

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Получить состояние источника", description = "Возвращает параметры, состояние чтения и последние результаты в буфере.")
    @GetMapping
    public AcquisitionService.Snapshot snapshot() { return service.snapshot(); }

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Изменить настройки", description = "Доступно только при остановленном источнике. Настройки и сброс очищают результаты и восстанавливают исходные брони.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true, content=@Content(examples=@ExampleObject(value="{\"samplePeriodMs\":1000,\"quantumPercent\":1,\"bufferCapacity\":10,\"averagingWindow\":3}")))
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PutMapping("/settings")
    public AcquisitionService.Snapshot configure(@RequestBody AcquisitionSettings settings) {
        return service.configure(settings);
    }

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Запустить чтение", description = "Запускает последовательное чтение CSV по таймеру. В конце файла чтение останавливается.")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping("/start")
    public AcquisitionService.Snapshot start() { return service.start(); }

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Остановить чтение", description = "Сохраняет таблицу измерений, но подсказка источника становится неактуальной.")
    @PostMapping("/stop")
    public AcquisitionService.Snapshot stop() { return service.stop(); }

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Сбросить источник", description = "При остановленном источнике очищает результаты, возвращает модельное время к началу файла и восстанавливает исходные брони.")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping("/reset")
    public AcquisitionService.Snapshot reset() { return service.reset(); }

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Прочитать записи вручную", description = "При остановленном источнике обрабатывает указанное число записей. Ручное чтение не делает подсказку актуальной.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping("/samples")
    public AcquisitionService.Snapshot readNext(@Parameter(description="Число записей от 1 до 10000", example="3") @RequestParam(defaultValue = "20") int count) {
        return service.readNext(count);
    }
}
