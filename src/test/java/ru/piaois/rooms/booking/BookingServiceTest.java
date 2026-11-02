package ru.piaois.rooms.booking;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.piaois.rooms.booking.BookingService.*;
import static ru.piaois.rooms.booking.DemoUsers.*;
class BookingServiceTest {
    final BookingService service=new BookingService();
    final User member=new User(2,"member",Role.MEMBER), reader=new User(1,"reader",Role.READER);
    final Instant start=Instant.parse("2026-10-15T08:00:00Z"), end=start.plusSeconds(3600);
    BookingRequest request(Instant a,Instant b,int count) { return new BookingRequest(1,a,b,count,"Занятие"); }
    @Test void createCancelAndAdjacentIntervals() {
        assertEquals(2,service.findAvailableRooms(reader,start,end,20).size());
        var booking=service.bookRoom(member,request(start,end,20));
        assertEquals(2,booking.ownerId());
        assertEquals(1,service.findAvailableRooms(reader,start,end,20).size());
        assertThrows(IllegalStateException.class,()->service.bookRoom(member,request(start,end,20)));
        service.bookRoom(member,request(end,end.plusSeconds(3600),20));
        assertEquals(Status.CANCELLED,service.cancelBooking(member,booking.id()).status());
        service.bookRoom(member,request(start,end,20));
        assertThrows(IllegalStateException.class,()->service.cancelBooking(member,booking.id()));
    }
    @Test void rolesAndOwnership() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.bookRoom(reader,request(start,end,1)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.cancelBooking(reader,1));
        var other=new User(3,"other",Role.MEMBER);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.cancelBooking(other,1));
        assertTrue(service.ownBookings(reader).isEmpty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.ownBookings(null));
    }
    @Test void validationAndBoundaryDuration() {
        for(var req:java.util.List.of(request(end,start,1),request(start,start,1),request(start,start.plusSeconds(7201),1),request(start,end,0),request(start,end,21),new BookingRequest(3,start,end,1,"Занятие"),new BookingRequest(1,start,end,1," ")))
            assertThrows(IllegalArgumentException.class,()->service.bookRoom(member,req));
        assertThrows(IllegalArgumentException.class,()->service.bookRoom(member,request(null,end,1)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.bookRoom(member,new BookingRequest(99,start,end,1,"Занятие")));
        assertNotNull(service.bookRoom(member,request(start,start.plusSeconds(7200),20)));
        assertThrows(IllegalStateException.class,()->service.bookRoom(member,request(start.plusSeconds(60),end,1)));
    }
    @Test void documentsRespectVisibilityAndCase() {
        assertEquals(1,service.searchDocuments(reader,"БРОНИРОВАНИЕ").size());
        assertTrue(service.searchDocuments(reader,"отменить").isEmpty());
        assertEquals(1,service.searchDocuments(member,"отменить").size());
        assertThrows(IllegalArgumentException.class,()->service.searchDocuments(reader," "));
    }
    @Test void concurrentRequestsCannotDoubleBook() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<Boolean> action=()->{gate.await();try {service.bookRoom(member,request(start,end,1));return true;}catch(IllegalStateException ex){return false;}};
            var a=pool.submit(action);var b=pool.submit(action);gate.countDown();
            assertNotEquals(a.get(3,TimeUnit.SECONDS),b.get(3,TimeUnit.SECONDS));
        }
    }
}
