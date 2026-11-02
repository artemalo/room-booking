package ru.piaois.rooms.booking;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingApiTest {
    @LocalServerPort int port;
    final HttpClient client=HttpClient.newHttpClient();
    HttpResponse<String> send(String method,String path,String credentials,String body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/booking"+path)).header("Content-Type","application/json");
        if(credentials!=null)b.header("Authorization","Basic "+Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void authenticationAndServerOwnedRole() throws Exception {
        assertEquals(401,send("GET","/me",null,null).statusCode());
        assertEquals(401,send("GET","/me","member:wrong",null).statusCode());
        assertTrue(send("GET","/me","reader:reader",null).body().contains("READER"));
        String body="{\"roomId\":1,\"start\":\"2026-10-15T06:00:00Z\",\"end\":\"2026-10-15T07:00:00Z\",\"participants\":5,\"purpose\":\"Test\"}";
        assertEquals(403,send("POST","/bookings","reader:reader",body).statusCode());
        assertEquals(400,send("POST","/bookings","member:member",body.replace("5,","5,\"ownerId\":1,")).statusCode());
        var created=send("POST","/bookings","member:member",body);
        assertEquals(200,created.statusCode());assertTrue(created.body().contains("\"ownerId\":2"));
        assertEquals(409,send("POST","/bookings","member:member",body).statusCode());
        var json=tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body());
        String id=json.get("id").asText();
        assertEquals(403,send("POST","/bookings/"+id+"/cancel","reader:reader",null).statusCode());
        assertEquals(200,send("POST","/bookings/"+id+"/cancel","member:member",null).statusCode());
        assertEquals(409,send("POST","/bookings/"+id+"/cancel","member:member",null).statusCode());
        assertEquals(200,send("GET","/rooms?start=2026-10-15T06:00:00Z&end=2026-10-15T07:00:00Z&participants=5","reader:reader",null).statusCode());
        assertEquals(200,send("GET","/documents?query=Test","reader:reader",null).statusCode());
    }
}
