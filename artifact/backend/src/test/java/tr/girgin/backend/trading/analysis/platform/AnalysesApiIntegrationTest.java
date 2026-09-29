package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole stack over HTTP: REST and SSE through the BFF, the analysis domain, SQLite, and a
 * shell script standing in for ta-runner.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnalysesApiIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final Path RUNNER = HOME.resolve("runner.sh");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    // A short run: four events, the last one ending it. The ticker named by SLOW_TICKER (set in
    // the secrets file, so it reaches the runner's environment) waits three seconds first.
    private static final String SCRIPT = """
            #!/bin/sh
            OUT="$5"
            [ "$SLOW_TICKER" != "" ] && grep -q "\\"ticker\\": *\\"$SLOW_TICKER\\"" "$3" && sleep 3
            # Like ta-runner: each event goes to <out>/events.jsonl first, then to stdout.
            line() { printf '{"v":1,"ts":"2026-09-29T10:00:0%sZ","run_id":"x","seq":%s,"type":"%s"%s}\\n' "$1" "$1" "$2" "$3" | tee -a "$OUT/events.jsonl"; }
            line 1 run_started ',"spec":{}'
            line 2 agent_status ',"agent":"Market Analyst","status":"in_progress"'
            sleep 0.2
            line 3 decision ',"rating":"Hold","raw":"**Rating**: Hold"'
            line 4 run_finished ',"status":"completed","report_dir":"/tmp"'
            """;

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        Files.writeString(RUNNER, SCRIPT);
        Files.writeString(HOME.resolve("secrets.env"), "SLOW_TICKER=SLOW\n");
        TestPlatformHome.register(registry, HOME, RUNNER);
    }

    @Test
    void startsARunAndStreamsItsEventsToTheEnd() throws Exception {
        HttpResponse<String> created = post("/api/analyses", request("NVDA"));

        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode analysis = JSON.readTree(created.body());
        String id = analysis.get("id").asString();
        assertThat(created.headers().firstValue("Location")).contains("/api/analyses/" + id);
        assertThat(analysis.get("status").asString()).isIn("QUEUED", "RUNNING");
        assertThat(analysis.at("/spec/ticker").asString()).isEqualTo("NVDA");
        assertThat(analysis.at("/spec/outputLanguage").asString()).isEqualTo("English");

        List<SseEvent> events = readStream(id, null);

        assertThat(events).extracting(SseEvent::id).containsExactly("1", "2", "3", "4", null);
        assertThat(events.getLast().name()).isEqualTo("end");
        JsonNode decision = JSON.readTree(events.get(2).data());
        assertThat(decision.get("type").asString()).isEqualTo("decision");
        assertThat(decision.at("/payload/rating").asString()).isEqualTo("Hold");

        JsonNode done = awaitStatus(id, "COMPLETED");
        assertThat(done.get("rating").asString()).isEqualTo("HOLD");
        assertThat(done.get("decision").asString()).isEqualTo("**Rating**: Hold");
        assertThat(done.get("endedAt").isNull()).isFalse();
    }

    @Test
    void resumesTheStreamAfterLastEventId() throws Exception {
        String id = JSON.readTree(post("/api/analyses", request("MU")).body()).get("id").asString();
        awaitStatus(id, "COMPLETED");

        List<SseEvent> events = readStream(id, "2");

        assertThat(events).extracting(SseEvent::id).containsExactly("3", "4", null);
    }

    @Test
    void rejectsASecondRunForTheSameTickerAndDate() throws Exception {
        String first = JSON.readTree(post("/api/analyses", request("SLOW")).body()).get("id").asString();

        HttpResponse<String> second = post("/api/analyses", request("slow"));

        assertThat(second.statusCode()).isEqualTo(409);
        JsonNode error = JSON.readTree(second.body());
        assertThat(error.get("errorCode").asString()).isEqualTo("already_running");
        assertThat(error.at("/params/id").asString()).isEqualTo(first);
        awaitStatus(first, "COMPLETED");
    }

    @Test
    void reportsInvalidInputWithAnErrorCodeAndField() throws Exception {
        HttpResponse<String> badTicker = post("/api/analyses", request("../etc"));
        HttpResponse<String> missingModel = post("/api/analyses", """
                {"ticker":"NVDA","tradeDate":"2026-09-25","analysts":["MARKET"],"llmProvider":"deepseek",
                 "quickThinkLlm":"deepseek-v4-flash"}""");
        HttpResponse<String> garbage = post("/api/analyses", "{not json");

        assertThat(badTicker.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(badTicker.body()).get("errorCode").asString()).isEqualTo("invalid_spec");
        assertThat(JSON.readTree(badTicker.body()).at("/params/field").asString()).isEqualTo("ticker");
        assertThat(missingModel.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(missingModel.body()).get("errorCode").asString()).isEqualTo("invalid_request");
        assertThat(JSON.readTree(missingModel.body()).at("/params/field").asString()).isEqualTo("deepThinkLlm");
        assertThat(garbage.statusCode()).isEqualTo(400);
    }

    @Test
    void unknownAnalysesAreNotFound() throws Exception {
        HttpResponse<String> unknown = get("/api/analyses/r_doesnotexist");
        HttpResponse<String> malformed = get("/api/analyses/..%2F..");

        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(unknown.body()).get("errorCode").asString()).isEqualTo("not_found");
        assertThat(malformed.statusCode()).isIn(400, 404);
    }

    @Test
    void listsAndFiltersRuns() throws Exception {
        String id = JSON.readTree(post("/api/analyses", request("GOOG")).body()).get("id").asString();
        awaitStatus(id, "COMPLETED");

        JsonNode list = JSON.readTree(get("/api/analyses?ticker=goog&status=COMPLETED").body());

        assertThat(list.isArray()).isTrue();
        assertThat(list.valueStream().map(n -> n.get("id").asString())).contains(id);
        assertThat(list.valueStream().map(n -> n.at("/spec/ticker").asString())).containsOnly("GOOG");
        assertThat(get("/api/analyses?status=BOGUS").statusCode()).isEqualTo(400);
    }

    private static String request(String ticker) {
        return """
                {"ticker":"%s","tradeDate":"2026-09-25","analysts":["MARKET"],"llmProvider":"deepseek",
                 "deepThinkLlm":"deepseek-v4-pro","quickThinkLlm":"deepseek-v4-flash"}""".formatted(ticker);
    }

    private JsonNode awaitStatus(String id, String status) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        JsonNode analysis;
        do {
            analysis = JSON.readTree(get("/api/analyses/" + id).body());
            if (analysis.get("status").asString().equals(status)) {
                return analysis;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Analysis " + id + " never reached " + status + ": " + analysis);
    }

    private List<SseEvent> readStream(String id, String lastEventId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/analyses/" + id + "/events"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "text/event-stream");
        if (lastEventId != null) {
            request.header("Last-Event-ID", lastEventId);
        }
        HttpResponse<java.io.InputStream> response =
                http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        List<SseEvent> events = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String eventId = null;
            String name = null;
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty() || name != null) {
                        events.add(new SseEvent(eventId, name, data.toString()));
                    }
                    eventId = null;
                    name = null;
                    data.setLength(0);
                } else if (line.startsWith("id:")) {
                    eventId = line.substring(3).strip();
                } else if (line.startsWith("event:")) {
                    name = line.substring(6).strip();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).strip());
                }
            }
        }
        return events;
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private record SseEvent(String id, String name, String data) {
    }
}
