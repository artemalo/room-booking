package ru.piaois.rooms.booking;
import org.junit.jupiter.api.Test;
import ru.piaois.rooms.measurement.*;
import java.time.*;
import java.io.StringReader;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static ru.piaois.rooms.measurement.AcquisitionService.Presence.*;

class RoomAdviceTest {
    final DemoUsers.User member=new DemoUsers.User(2,"member",DemoUsers.Role.MEMBER);
    final BookingService bookings=new BookingService();
    final Instant first=Instant.parse("2026-10-15T09:00:00Z");
    CsvMeasurementSource source(int room,int value) throws Exception {
        StringBuilder csv=new StringBuilder("measuredAt,roomId,occupancyPercent\n");
        for(int i=0;i<12;i++)csv.append(first.plusSeconds(i)).append(',').append(room).append(',').append(value).append('\n');
        return new CsvMeasurementSource(new StringReader(csv.toString()));
    }
    void await(AcquisitionService s,long count)throws Exception {
        long end=System.nanoTime()+3_000_000_000L;
        while(s.snapshot().generated()<count && System.nanoTime()<end)Thread.sleep(5);
        assertTrue(s.snapshot().generated()>=count);
    }
    @Test void warmupFreshnessAndResume()throws Exception {
        AtomicLong nano=new AtomicLong();
        try(var s=new Fixture(source(1,5),nano,bookings)) {
            var a=s.a; a.configure(new AcquisitionSettings(500,1,10,3));a.start();await(a,1);
            assertEquals(WARMING_UP,a.current().state());await(a,3);assertEquals(ABSENT,a.current().state());
            nano.set(5_000_000_001L);assertEquals(NO_DATA,a.current().state());
            await(a,4);assertEquals(WARMING_UP,a.current().state());await(a,6);assertEquals(ABSENT,a.current().state());
            long count=a.stop().generated();assertEquals(NO_DATA,a.current().state());assertFalse(a.snapshot().measurements().isEmpty());
            a.start();await(a,count+1);assertEquals(WARMING_UP,a.current().state());
        }
    }
    @Test void nowRecommendationFutureAndRoomIsolation()throws Exception {
        try(var s=new Fixture(source(1,0),new AtomicLong(),bookings)) {
            var a=s.a;a.configure(new AcquisitionSettings(100,1,10,3));a.start();await(a,3);
            var api=new RoomAdviceService(bookings,a);var result=api.findNow(member,30,10);
            assertTrue(result.demonstration());assertEquals(result.demoTime(),result.start());
            assertTrue(result.rooms().getFirst().recommended());
            assertEquals(NO_DATA,result.rooms().get(1).currentUse().state());
            var future=api.findAt(member,first.plusSeconds(86400),first.plusSeconds(88200),10);
            assertTrue(future.rooms().stream().noneMatch(RoomAdviceService.Offer::recommended));
            var created=api.bookNow(member,new RoomAdviceService.NowRequest(1,30,10,"Урок",false));
            assertFalse(created.booking().start().isBefore(result.start()));
            assertEquals(Duration.ofMinutes(30),Duration.between(created.booking().start(),created.booking().end()));
            assertTrue(api.findNow(member,30,10).rooms().stream().noneMatch(o->o.room().id()==1));
            bookings.cancelBooking(member,created.booking().id());
            assertTrue(api.findNow(member,30,10).rooms().stream().anyMatch(o->o.room().id()==1));
        }
    }
    @Test void presenceWarnsButDoesNotCancelOrForbidAcknowledgedBooking()throws Exception {
        try(var s=new Fixture(source(1,50),new AtomicLong(),bookings)) {
            var a=s.a;a.configure(new AcquisitionSettings(100,1,10,3));a.start();await(a,3);
            var api=new RoomAdviceService(bookings,a);
            assertEquals(PRESENT,api.findNow(member,30,10).rooms().getFirst().currentUse().state());
            assertThrows(IllegalStateException.class,()->api.bookNow(member,new RoomAdviceService.NowRequest(1,30,10,"Урок",false)));
            var created=api.bookNow(member,new RoomAdviceService.NowRequest(1,30,10,"Урок",true));assertNotNull(created.warning());
            assertEquals(BookingService.Status.ACTIVE,created.booking().status());
            await(a,12);assertEquals(NO_DATA,a.current().state());assertEquals(10,a.snapshot().retained());
            assertEquals(3,bookings.ownBookings(member).size());
            a.reset();assertEquals(first,a.current().demoTime());assertEquals(2,bookings.ownBookings(member).size());
            assertEquals(NO_DATA,a.current().state());assertEquals(0,a.snapshot().retained());
        }
    }
    @Test void unknownRoomRejected()throws Exception {
        var source=source(99,0);
        assertThrows(IllegalArgumentException.class,()->new AcquisitionService(source,"test",System::nanoTime,Clock.systemUTC(),bookings));
    }
    @Test void scenarioAverageLagsAndFutureBookingIgnoresPresence()throws Exception {
        var csv=new CsvMeasurementSource(java.nio.file.Files.newBufferedReader(java.nio.file.Path.of("data/occupancy.csv")));
        try(var s=new Fixture(csv,new AtomicLong(),bookings)) {
            var a=s.a;
            a.readNext(12);assertEquals(5.0,a.current().average());
            a.readNext(1);assertEquals(35.0/3,a.current().average(),0.000001);
            a.readNext(27);assertEquals(35.0/3,a.current().average(),0.000001);
            a.readNext(1);assertEquals(5.0,a.current().average());
            assertEquals(NO_DATA,a.current().state()); // Пакетное чтение не является живым источником.
        }
        try(var s=new Fixture(source(1,50),new AtomicLong(),bookings)) {
            var a=s.a;a.configure(new AcquisitionSettings(100,1,10,3));a.start();await(a,3);
            var api=new RoomAdviceService(bookings,a);
            var future=api.bookAt(member,new BookingService.BookingRequest(1,first.plusSeconds(86400),first.plusSeconds(88200),10,"Завтра"));
            assertEquals(BookingService.Status.ACTIVE,future.status());
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->api.bookNow(new DemoUsers.User(1,"reader",DemoUsers.Role.READER),new RoomAdviceService.NowRequest(1,30,10,"Урок",true)));
        }
    }
    static class Fixture implements AutoCloseable {
        final AcquisitionService a;
        Fixture(CsvMeasurementSource source,AtomicLong nano,BookingService bookings){a=new AcquisitionService(source,"test",nano::get,Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"),ZoneOffset.UTC),bookings);}
        public void close(){a.close();}
    }
}
