package ru.piaois.rooms.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import static ru.piaois.rooms.booking.BookingService.*;

@RestController
@RequestMapping("/api/booking")
@Tag(name = "Бронирование", description = "Поиск правил, аудиторий и управление собственными бронями")
@SecurityRequirement(name = "basicAuth")
public class BookingController {
    private final BookingService service;
    private final DemoUsers users;
    private final RoomAdviceService advice;
    public BookingController(BookingService service, DemoUsers users, RoomAdviceService advice) {
        this.service = service; this.users = users; this.advice = advice;
    }

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Проверить вход", description = "Возвращает идентификатор, логин и роль текущего пользователя.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @GetMapping("/me")
    public DemoUsers.User me(@Parameter(hidden = true) @RequestHeader(value="Authorization", required=false) String auth) { return users.authenticate(auth); }

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Найти правила", description = "Поиск по названию и тексту. Памятка об отмене доступна только MEMBER.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @GetMapping("/documents")
    public List<SearchResult> searchDocuments(@Parameter(hidden=true) @RequestHeader(value="Authorization",required=false) String auth,
            @Parameter(description="Слово или фраза, от 1 до 200 символов", example="бронирование") @RequestParam String query) { return service.searchDocuments(users.authenticate(auth),query); }

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Найти аудитории на выбранное время", description = "Показывает активные аудитории достаточной вместимости без пересекающихся активных броней. Текущие измерения не прогнозируют будущее.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @GetMapping("/rooms")
    public RoomAdviceService.Search findAvailableRooms(@Parameter(hidden=true) @RequestHeader(value="Authorization",required=false) String auth,
            @Parameter(description="Начало в формате ISO 8601", example="2026-10-15T09:00:00Z") @RequestParam Instant start,
            @Parameter(description="Окончание, не позднее двух часов после начала", example="2026-10-15T09:30:00Z") @RequestParam Instant end,
            @Parameter(description="Положительное число участников", example="10") @RequestParam int participants) { return advice.findAt(users.authenticate(auth),start,end,participants); }

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Найти аудитории на сейчас", description = "Начало определяется модельным временем последней записи CSV. Результат включает текущую подсказку источника.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @GetMapping("/rooms/now")
    public RoomAdviceService.Search findNow(@Parameter(hidden=true) @RequestHeader(value="Authorization",required=false) String auth,
            @Parameter(description="Длительность от 1 до 120 минут", example="30") @RequestParam int durationMinutes,
            @Parameter(description="Положительное число участников", example="10") @RequestParam int participants) { return advice.findNow(users.authenticate(auth),durationMinutes,participants); }

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Создать бронь на сейчас", description = "Только MEMBER. Сервер заново берет модельное время и проверяет доступность. При признаках присутствия нужно подтвердить предупреждение через acknowledgePresence.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true, content=@Content(examples=@ExampleObject(value="{\"roomId\":1,\"durationMinutes\":30,\"participants\":10,\"purpose\":\"Занятие\",\"acknowledgePresence\":false}")))
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping("/bookings/now")
    public RoomAdviceService.Confirmation bookNow(@Parameter(hidden=true) @RequestHeader(value="Authorization",required=false) String auth, @RequestBody RoomAdviceService.NowRequest request) {return advice.bookNow(users.authenticate(auth),request);}

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Создать бронь на выбранное время", description = "Только MEMBER. Цель обязательна, длительность не больше двух часов. Текущие измерения не влияют на будущий интервал.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true, content=@Content(examples=@ExampleObject(value="{\"roomId\":1,\"start\":\"2026-10-15T09:00:00Z\",\"end\":\"2026-10-15T09:30:00Z\",\"participants\":10,\"purpose\":\"Занятие\"}")))
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping("/bookings")
    public Booking bookRoom(@Parameter(hidden=true) @RequestHeader(value="Authorization",required=false) String auth, @RequestBody BookingRequest request) { return advice.bookAt(users.authenticate(auth),request); }

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Получить собственные брони", description = "Возвращает активные и отмененные брони текущего пользователя.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @GetMapping("/bookings")
    public List<Booking> ownBookings(@Parameter(hidden=true) @RequestHeader(value="Authorization",required=false) String auth) { return service.ownBookings(users.authenticate(auth)); }

    @ApiResponse(responseCode = "200", description = "Успешный ответ")
    @Operation(summary = "Отменить бронь", description = "Только MEMBER. Можно отменить собственную активную бронь. Отмененная бронь больше не занимает интервал.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping("/bookings/{id}/cancel")
    public Booking cancelBooking(@Parameter(hidden=true) @RequestHeader(value="Authorization",required=false) String auth,
            @Parameter(description="Идентификатор собственной брони", example="1") @PathVariable long id) { return service.cancelBooking(users.authenticate(auth),id); }
}
