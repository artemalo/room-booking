package ru.piaois.rooms.booking;

import org.springframework.stereotype.Service;
import ru.piaois.rooms.measurement.AcquisitionService;
import java.time.Instant;
import java.util.*;
import static ru.piaois.rooms.booking.BookingService.*;
import static ru.piaois.rooms.measurement.AcquisitionService.Presence;

@Service
public class RoomAdviceService {
    private final BookingService bookings;
    private final AcquisitionService measurements;
    public RoomAdviceService(BookingService bookings, AcquisitionService measurements) { this.bookings=bookings;this.measurements=measurements; }
    public record Advice(Presence state, String label, Instant measuredAt, Instant receivedAt, Double average) {}
    public record Offer(Room room, Advice currentUse, boolean recommended, String explanation) {}
    public record Search(boolean demonstration, Instant demoTime, Instant start, Instant end, boolean startsNow, List<Offer> rooms) {}
    public record NowRequest(long roomId, int durationMinutes, int participants, String purpose, boolean acknowledgePresence) {}
    public record Confirmation(Booking booking, Advice currentUse, String warning, boolean demonstration) {}
    private static final String WARNING="По данным источника в аудитории могут находиться люди. Проверьте помещение перед началом занятия";
    private Advice advice(Room room, AcquisitionService.Current current) {
        boolean same=room.id()==current.roomId();
        Presence state=same?current.state():Presence.NO_DATA;
        String label=switch(state) {
            case NO_DATA -> "Нет актуальных данных";
            case WARMING_UP -> "Уточняется";
            case ABSENT -> "По данным источника присутствие не обнаружено";
            case PRESENT -> "По данным источника есть признаки присутствия";
        };
        return new Advice(state,label,same?current.measuredAt():null,same?current.receivedAt():null,same?current.average():null);
    }
    private static void duration(int minutes) { if(minutes<1 || minutes>120)throw new IllegalArgumentException("Длительность от 1 до 120 минут"); }
    public Search findNow(DemoUsers.User user,int minutes,int participants) {
        duration(minutes);
        synchronized(measurements) {
            var current=measurements.current();
            return search(user,current.demoTime(),current.demoTime().plusSeconds(minutes*60L),participants,true,current);
        }
    }
    public Search findAt(DemoUsers.User user,Instant start,Instant end,int participants) {
        synchronized(measurements) {return search(user,start,end,participants,false,measurements.current());}
    }
    private Search search(DemoUsers.User user,Instant start,Instant end,int participants,boolean now,AcquisitionService.Current current) {
        var offers=bookings.findAvailableRooms(user,start,end,participants).stream().map(room -> {
            var advice=advice(room,current);
            boolean preferred=now && advice.state()==Presence.ABSENT;
            String explanation=!now?"Доступна по броням. Данные источника относятся к моменту сейчас и не прогнозируют выбранный интервал":
                preferred?"Доступна по броням; свежие данные не обнаруживают присутствия":
                advice.state()==Presence.PRESENT?WARNING:"Доступна по броням; достоверной подсказки источника пока нет";
            return new Offer(room,advice,preferred,explanation);
        }).sorted(Comparator.comparing(Offer::recommended).reversed().thenComparing(o -> o.room().id())).toList();
        return new Search(true,current.demoTime(),start,end,now,offers);
    }
    public Confirmation bookNow(DemoUsers.User user,NowRequest request) {
        BookingService.requireMember(user);
        if(request==null)throw new IllegalArgumentException("Нужны параметры брони");
        duration(request.durationMinutes());
        synchronized(measurements) {
            var current=measurements.current();
            var available=bookings.findAvailableRooms(user,current.demoTime(),current.demoTime().plusSeconds(request.durationMinutes()*60L),request.participants());
            var room=available.stream().filter(r -> r.id()==request.roomId()).findFirst().orElseThrow(() -> new IllegalStateException("Аудитория недоступна на выбранный интервал"));
            var advice=advice(room,current);
            if(advice.state()==Presence.PRESENT && !request.acknowledgePresence())throw new IllegalStateException(WARNING+". Подтвердите ознакомление с предупреждением");
            var booking=bookings.bookRoom(user,new BookingRequest(room.id(),current.demoTime(),current.demoTime().plusSeconds(request.durationMinutes()*60L),request.participants(),request.purpose()));
            return new Confirmation(booking,advice,advice.state()==Presence.PRESENT?WARNING:null,true);
        }
    }
    public Booking bookAt(DemoUsers.User user,BookingRequest request) {
        synchronized(measurements) {return bookings.bookRoom(user,request);}
    }
}
