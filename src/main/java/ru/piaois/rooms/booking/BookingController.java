package ru.piaois.rooms.booking;

import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import static ru.piaois.rooms.booking.BookingService.*;

@RestController
@RequestMapping("/api/booking")
public class BookingController {
    private final BookingService service;
    private final DemoUsers users;
    private final RoomAdviceService advice;
    public BookingController(BookingService service,DemoUsers users,RoomAdviceService advice) { this.service=service;this.users=users;this.advice=advice; }
    @GetMapping("/me")
    public DemoUsers.User me(@RequestHeader(value="Authorization",required=false) String auth) { return users.authenticate(auth); }
    @GetMapping("/documents")
    public List<SearchResult> searchDocuments(@RequestHeader(value="Authorization",required=false) String auth,@RequestParam String query) { return service.searchDocuments(users.authenticate(auth),query); }
    @GetMapping("/rooms")
    public RoomAdviceService.Search findAvailableRooms(@RequestHeader(value="Authorization",required=false) String auth,@RequestParam Instant start,@RequestParam Instant end,@RequestParam int participants) { return advice.findAt(users.authenticate(auth),start,end,participants); }
    @GetMapping("/rooms/now")
    public RoomAdviceService.Search findNow(@RequestHeader(value="Authorization",required=false) String auth,@RequestParam int durationMinutes,@RequestParam int participants) {return advice.findNow(users.authenticate(auth),durationMinutes,participants);}
    @PostMapping("/bookings/now")
    public RoomAdviceService.Confirmation bookNow(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody RoomAdviceService.NowRequest request) {return advice.bookNow(users.authenticate(auth),request);}
    @PostMapping("/bookings")
    public Booking bookRoom(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody BookingRequest request) { return advice.bookAt(users.authenticate(auth),request); }
    @GetMapping("/bookings")
    public List<Booking> ownBookings(@RequestHeader(value="Authorization",required=false) String auth) { return service.ownBookings(users.authenticate(auth)); }
    @PostMapping("/bookings/{id}/cancel")
    public Booking cancelBooking(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable long id) { return service.cancelBooking(users.authenticate(auth),id); }
}
