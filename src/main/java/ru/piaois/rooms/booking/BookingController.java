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
    public BookingController(BookingService service,DemoUsers users) { this.service=service;this.users=users; }
    @GetMapping("/me")
    public DemoUsers.User me(@RequestHeader(value="Authorization",required=false) String auth) { return users.authenticate(auth); }
    @GetMapping("/documents")
    public List<SearchResult> searchDocuments(@RequestHeader(value="Authorization",required=false) String auth,@RequestParam String query) { return service.searchDocuments(users.authenticate(auth),query); }
    @GetMapping("/rooms")
    public List<Room> findAvailableRooms(@RequestHeader(value="Authorization",required=false) String auth,@RequestParam Instant start,@RequestParam Instant end,@RequestParam int participants) { return service.findAvailableRooms(users.authenticate(auth),start,end,participants); }
    @PostMapping("/bookings")
    public Booking bookRoom(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody BookingRequest request) { return service.bookRoom(users.authenticate(auth),request); }
    @GetMapping("/bookings")
    public List<Booking> ownBookings(@RequestHeader(value="Authorization",required=false) String auth) { return service.ownBookings(users.authenticate(auth)); }
    @PostMapping("/bookings/{id}/cancel")
    public Booking cancelBooking(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable long id) { return service.cancelBooking(users.authenticate(auth),id); }
}
