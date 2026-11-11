package ru.piaois.rooms.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiTest {
    @LocalServerPort int port;

    @Test
    void documentsResponsesAndSeparatesAuthentication() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        var doc = JsonMapper.builder().build().readTree(response.body());
        var booking = doc.at("/paths/~1api~1booking~1me/get");
        var acquisition = doc.at("/paths/~1api~1acquisition/get");
        assertTrue(booking.path("responses").has("200"));
        assertTrue(booking.path("responses").has("401"));
        assertTrue(booking.path("security").get(0).has("basicAuth"));
        assertTrue(acquisition.path("responses").has("200"));
        assertFalse(acquisition.has("security"));
        assertEquals("basic", doc.at("/components/securitySchemes/basicAuth/scheme").asText());
    }
}
