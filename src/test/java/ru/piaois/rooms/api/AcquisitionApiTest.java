package ru.piaois.rooms.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import ru.piaois.rooms.measurement.AcquisitionService;
import ru.piaois.rooms.measurement.AcquisitionSettings;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AcquisitionApiTest {
    @LocalServerPort int port;
    @Autowired AcquisitionService service;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void reset() {
        service.stop();
        service.configure(AcquisitionSettings.defaults());
    }

    @Test
    void servesPageAndMeasurementJson() throws Exception {
        assertTrue(send("GET", "/", null).body().contains("Учебные измерения занятости"));
        var response = send("POST", "/api/acquisition/samples?count=3", null);
        assertEquals(200, response.statusCode());
        JsonNode body = json(response);
        assertEquals(3, body.get("generated").asInt());
        assertEquals(10, body.get("measurements").get(0).get("filteredValue").asDouble());
        assertEquals(15, body.get("measurements").get(1).get("filteredValue").asDouble());
        assertEquals(20, body.get("measurements").get(2).get("filteredValue").asDouble());
        assertEquals(1000, body.get("settings").get("samplePeriodMs").asInt());
        assertEquals(1, body.get("settings").get("quantumPercent").asDouble());
        assertEquals(3, body.get("settings").get("averagingWindow").asInt());
        assertEquals(10, body.get("settings").get("bufferCapacity").asInt());
        assertEquals(60, body.get("totalRecords").asInt());
        assertTrue(body.get("measurements").get(0).has("measuredAt"));
    }

    @Test
    void appliesSettingsAndReturnsReadableValidationErrors() throws Exception {
        String settings = """
                {"samplePeriodMs":100,"quantumPercent":0.5,"bufferCapacity":10,"averagingWindow":3}
                """;
        assertEquals(200, send("PUT", "/api/acquisition/settings", settings).statusCode());
        assertEquals(100, service.snapshot().settings().samplePeriodMs());
        var invalid = send("PUT", "/api/acquisition/settings", settings.replace("\"averagingWindow\":3", "\"averagingWindow\":30"));
        assertEquals(400, invalid.statusCode());
        assertTrue(json(invalid).get("message").asText().contains("Окно среднего"));
        assertEquals(3, service.snapshot().settings().averagingWindow());
        assertEquals(400, send("PUT", "/api/acquisition/settings", "{}").statusCode());
        assertEquals(400, send("PUT", "/api/acquisition/settings", "not-json").statusCode());
        assertEquals(400, send("PUT", "/api/acquisition/settings", settings.replace("\"samplePeriodMs\":100", "\"samplePeriodMs\":null")).statusCode());
        assertEquals(400, send("PUT", "/api/acquisition/settings", settings.replace("\"samplePeriodMs\":100", "\"unexpected\":2,\"samplePeriodMs\":100")).statusCode());
        assertEquals(400, send("POST", "/api/acquisition/samples?count=0", null).statusCode());
        assertEquals(400, send("POST", "/api/acquisition/samples?count=wrong", null).statusCode());
    }

    @Test
    void preventsChangingSeriesWhileRunning() throws Exception {
        assertEquals(200, send("POST", "/api/acquisition/start", null).statusCode());
        assertEquals(409, send("POST", "/api/acquisition/reset", null).statusCode());
        assertEquals(409, send("POST", "/api/acquisition/samples", null).statusCode());
        assertEquals(200, send("POST", "/api/acquisition/stop", null).statusCode());
        assertEquals(200, send("POST", "/api/acquisition/reset", null).statusCode());
        assertEquals(0, json(send("GET", "/api/acquisition", null)).get("generated").asInt());
    }

    private JsonNode json(HttpResponse<String> response) { return mapper.readTree(response.body()); }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
