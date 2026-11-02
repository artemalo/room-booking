package ru.piaois.rooms.booking;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static ru.piaois.rooms.booking.DemoUsers.*;

@Service
public class BookingService {
    public record Room(long id, String name, int capacity, boolean active) {}
    public enum Status { ACTIVE, CANCELLED }
    public record Booking(long id, long ownerId, long roomId, Instant start, Instant end,
                          int participants, String purpose, Status status) {}
    private record Document(long id, String title, String source, String text, Role visibility) {}
    public record SearchResult(long id, String title, String source, String excerpt) {}
    public record BookingRequest(long roomId, Instant start, Instant end, int participants, String purpose) {}
    private final List<Room> rooms = List.of(new Room(1,"101",20,true),new Room(2,"102",30,true),new Room(3,"103",10,false));
    private final List<Document> documents = List.of(
        new Document(1,"Правила бронирования","Учебные правила", "Бронирование аудитории допускается на срок до двух часов. Укажите число участников и цель занятия.",Role.READER),
        new Document(2,"Отмена брони","Памятка участника", "Участник может отменить только собственную активную бронь.",Role.MEMBER));
    private final Map<Long,Booking> bookings = new LinkedHashMap<>();
    private long nextId = 3;
    public BookingService() {
        bookings.put(1L,new Booking(1,2,1,Instant.parse("2026-10-15T10:00:00Z"),Instant.parse("2026-10-15T11:00:00Z"),10,"Учебное занятие",Status.ACTIVE));
        bookings.put(2L,new Booking(2,2,2,Instant.parse("2026-10-15T11:00:00Z"),Instant.parse("2026-10-15T12:00:00Z"),15,"Семинар",Status.ACTIVE));
    }
    public List<SearchResult> searchDocuments(User user, String query) {
        requireUser(user);
        if (query == null || query.isBlank() || query.length()>200) throw new IllegalArgumentException("Введите запрос длиной от 1 до 200 символов");
        String q=query.strip().toLowerCase(Locale.ROOT);
        return documents.stream().filter(d -> d.visibility()==Role.READER || user.role()==Role.MEMBER)
            .filter(d -> (d.title()+" "+d.text()).toLowerCase(Locale.ROOT).contains(q))
            .map(d -> new SearchResult(d.id(),d.title(),d.source(),d.text())).toList();
    }
    public synchronized List<Room> findAvailableRooms(User user, Instant start, Instant end, int participants) {
        requireUser(user); validate(start,end,participants);
        return rooms.stream().filter(r -> r.active() && r.capacity()>=participants && !conflicts(r.id(),start,end)).toList();
    }
    public synchronized Booking bookRoom(User user, BookingRequest request) {
        requireMember(user);
        if (request==null) throw new IllegalArgumentException("Нужны параметры брони");
        validate(request.start(),request.end(),request.participants());
        if (request.purpose()==null || request.purpose().isBlank() || request.purpose().length()>200) throw new IllegalArgumentException("Укажите цель длиной от 1 до 200 символов");
        Room room=rooms.stream().filter(r -> r.id()==request.roomId()).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Аудитория не найдена"));
        if (!room.active() || request.participants()>room.capacity()) throw new IllegalArgumentException("Аудитория недоступна или недостаточно мест");
        if (conflicts(room.id(),request.start(),request.end())) throw new IllegalStateException("Интервал уже занят");
        Booking booking=new Booking(nextId++,user.id(),room.id(),request.start(),request.end(),request.participants(),request.purpose().strip(),Status.ACTIVE);
        bookings.put(booking.id(),booking); return booking;
    }
    public synchronized Booking cancelBooking(User user, long id) {
        requireMember(user);
        Booking old=bookings.get(id);
        if (old==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Бронь не найдена");
        if (old.ownerId()!=user.id()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Можно отменить только собственную бронь");
        if (old.status()!=Status.ACTIVE) throw new IllegalStateException("Бронь уже отменена");
        Booking cancelled=new Booking(old.id(),old.ownerId(),old.roomId(),old.start(),old.end(),old.participants(),old.purpose(),Status.CANCELLED);
        bookings.put(id,cancelled);return cancelled;
    }
    public synchronized List<Booking> ownBookings(User user) {
        requireUser(user); return bookings.values().stream().filter(b -> b.ownerId()==user.id()).toList();
    }
    private boolean conflicts(long room,Instant start,Instant end) {
        return bookings.values().stream().anyMatch(b -> b.roomId()==room && b.status()==Status.ACTIVE && start.isBefore(b.end()) && end.isAfter(b.start()));
    }
    private static void validate(Instant start,Instant end,int participants) {
        if (start==null || end==null || !start.isBefore(end) || Duration.between(start,end).compareTo(Duration.ofHours(2))>0) throw new IllegalArgumentException("Начало должно быть раньше окончания, длительность не более двух часов");
        if (participants<1) throw new IllegalArgumentException("Число участников должно быть положительным");
    }
    private static void requireUser(User user) {
        if (user==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Требуется вход");
    }
    private static void requireMember(User user) {
        requireUser(user); if(user.role()!=Role.MEMBER) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Требуется роль MEMBER");
    }
}
