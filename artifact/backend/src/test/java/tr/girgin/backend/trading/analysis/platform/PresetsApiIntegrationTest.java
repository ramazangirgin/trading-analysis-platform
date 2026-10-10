package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/** The preset API over HTTP, on a migrated PostgreSQL database: a stale rename is a 409. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class PresetsApiIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestPlatformHome.register(registry, HOME, HOME.resolve("runner.sh"));
    }

    @Test
    void aStaleRenameIsAConflictAndLeavesTheFirstRename() throws Exception {
        JsonNode created = JSON.readTree(
                send("POST", "/api/presets", body("Original", null)).body());
        String id = created.get("id").asString();
        assertThat(created.get("version").asLong()).isZero();

        HttpResponse<String> first = send("PUT", "/api/presets/" + id, body("First", 0L));
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(first.body()).get("version").asLong()).isEqualTo(1);

        HttpResponse<String> stale = send("PUT", "/api/presets/" + id, body("Second", 0L));
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(stale.body()).get("errorCode").asString()).isEqualTo("concurrent_update");

        JsonNode stored = null;
        for (JsonNode preset : JSON.readTree(send("GET", "/api/presets", null).body())) {
            if (id.equals(preset.get("id").asString())) {
                stored = preset;
            }
        }
        assertThat(stored).isNotNull();
        assertThat(stored.get("name").asString()).isEqualTo("First");
        assertThat(stored.get("version").asLong()).isEqualTo(1);
    }

    @Test
    void anUpdateWithoutAVersionAppliesToWhatIsStored() throws Exception {
        String id = JSON.readTree(
                        send("POST", "/api/presets", body("Plain", null)).body())
                .get("id")
                .asString();

        HttpResponse<String> updated = send("PUT", "/api/presets/" + id, body("Renamed", null));

        assertThat(updated.statusCode()).isEqualTo(200);
        JsonNode preset = JSON.readTree(updated.body());
        assertThat(preset.get("name").asString()).isEqualTo("Renamed");
        assertThat(preset.get("version").asLong()).isEqualTo(1);
    }

    private static String body(String name, Long version) {
        return "{\"name\":\"" + name + "\",\"values\":{\"ticker\":\"NVDA\"}"
                + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
