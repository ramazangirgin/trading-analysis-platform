package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/** Sharing through the API: who may publish, what others may do with a public analysis, and who takes it back. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class AnalysisSharingIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final Path RUNNER = HOME.resolve("runner.sh");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NOT_FOUND = "not_found";
    private static final String PUBLIC = "PUBLIC";
    private static final String PRIVATE = "PRIVATE";

    // Like ta-runner: each event goes to <out>/events.jsonl first, then to stdout.
    @SuppressWarnings("checkstyle:LineLength") // runner protocol lines in a fixture stay on one line
    private static final String SCRIPT = """
            #!/bin/sh
            if [ "$1" = "catalog" ]; then
            cat <<'JSON'
            {"upstream_version":"0.5.1","defaults":{"llm_provider":"deepseek","deep_think_llm":"deepseek-v4-pro","quick_think_llm":"deepseek-v4-flash"},"providers":[{"id":"deepseek","api_key_env":"DEEPSEEK_API_KEY","custom_model_allowed":true,"quick_models":[{"id":"deepseek-v4-flash","label":"DeepSeek V4 Flash"}],"deep_models":[{"id":"deepseek-v4-pro","label":"DeepSeek V4 Pro"}]}],"analysts":[{"id":"market","agent":"Market Analyst"}],"asset_types":["stock","crypto"]}
            JSON
            exit 0
            fi
            OUT="$5"
            echo "INFO loading tradingagents" >&2
            line() { printf '{"v":1,"ts":"2026-09-29T10:00:0%sZ","run_id":"x","seq":%s,"type":"%s"%s}\\n' "$1" "$1" "$2" "$3" | tee -a "$OUT/events.jsonl"; }
            line 1 run_started ',"spec":{}'
            line 2 decision ',"rating":"Hold","raw":"**Rating**: Hold"'
            line 3 run_finished ',"status":"completed","report_dir":"/tmp"'
            """;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private PasswordHasherPort hasher;

    @Autowired
    private AnalysisRepositoryPort analyses;

    @Autowired
    private DataSource dataSource;

    private String analystAName;
    private String analystBName;
    private TestSession analystA;
    private TestSession analystB;
    private TestSession admin;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        Files.writeString(RUNNER, SCRIPT);
        TestPlatformHome.register(registry, HOME, RUNNER);
    }

    @BeforeEach
    void logIn() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM \"SETTINGS\".\"ANALYSIS_DEFAULTS\"");
        }
        analystAName = TestSession.createUser(users, hasher, true, RoleId.ANALYST);
        analystBName = TestSession.createUser(users, hasher, true, RoleId.ANALYST);
        analystA = loggedInAs(analystAName);
        analystB = loggedInAs(analystBName);
        admin = loggedInAs(TestSession.createUser(users, hasher, true, RoleId.ADMIN));
    }

    @Test
    void publishingNeedsAnalysisShareAndTheRoleAppliesAtTheNextLogin() throws Exception {
        String id = startAndAwait(analystA, "SHRA");
        JsonNode before = get(analystA, id);
        assertThat(before.get("visibility").asString()).isEqualTo(PRIVATE);
        assertThat(before.get("canChangeVisibility").asBoolean()).isFalse();

        HttpResponse<String> refused = setVisibility(analystA, id, PUBLIC, version(before));

        assertError(refused, 403, "analysis_share_forbidden");

        grantPublisher(analystAName);
        assertThat(get(analystA, id).get("canChangeVisibility").asBoolean()).isFalse();
        analystA = loggedInAs(analystAName);
        assertThat(get(analystA, id).get("canChangeVisibility").asBoolean()).isTrue();

        HttpResponse<String> shared = setVisibility(analystA, id, PUBLIC, version(before));

        assertThat(shared.statusCode()).isEqualTo(200);
        JsonNode after = JSON.readTree(shared.body());
        assertThat(after.get("visibility").asString()).isEqualTo(PUBLIC);
        assertThat(after.get("visibilityChangedAt").isNull()).isFalse();
        assertThat(version(after)).isGreaterThan(version(before));
    }

    @Test
    void onlyACompletedAnalysisCanBeMadePublic() throws Exception {
        grantPublisher(analystAName);
        analystA = loggedInAs(analystAName);
        OwnerId owner = ownerOf(analystAName);
        AnalysisId running = AnalysisId.newId();
        analyses.insert(Analysis.queued(running, spec("SHRB"), owner, Instant.now()));
        analyses.update(analyses.findById(running).orElseThrow().running(Instant.now(), "ref"));
        AnalysisId failed = AnalysisId.newId();
        analyses.insert(Analysis.queued(failed, spec("SHRC"), owner, Instant.now()));
        analyses.update(analyses.findById(failed)
                .orElseThrow()
                .finished(AnalysisStatus.FAILED, Instant.now(), "boom", "failed"));

        for (AnalysisId id : List.of(running, failed)) {
            JsonNode dto = get(analystA, id.value());

            HttpResponse<String> response = setVisibility(analystA, id.value(), PUBLIC, version(dto));

            assertError(response, 409, "analysis_not_shareable");
            assertThat(JSON.readTree(response.body()).at("/params/status").asString())
                    .isEqualTo(dto.get("status").asString());
        }
    }

    @Test
    void everyoneElseReadsAPublicAnalysisButCannotChangeIt() throws Exception {
        String id = startAndAwait(analystA, "SHRD");
        makePublic(id);

        JsonNode listed = JSON.readTree(
                        analystB.send("GET", "/api/analyses?scope=SHARED", null).body())
                .valueStream()
                .filter(a -> a.get("id").asString().equals(id))
                .findFirst()
                .orElseThrow();
        assertThat(listed.at("/owner/username").asString()).isEqualTo(analystAName);
        assertThat(ids(analystA.send("GET", "/api/analyses?scope=SHARED", null)))
                .doesNotContain(id);
        assertThat(ids(analystB.send("GET", "/api/analyses", null))).doesNotContain(id);

        JsonNode seen = get(analystB, id);
        assertThat(seen.get("canManage").asBoolean()).isFalse();
        assertThat(seen.get("canChangeVisibility").asBoolean()).isFalse();
        assertThat(JSON.readTree(analystB.send("GET", "/api/analyses/" + id + "/report", null)
                                .body())
                        .get("errorCode")
                        .asString())
                .isEqualTo("report_not_found");
        assertThat(JSON.readTree(analystB.send("GET", "/api/analyses/" + id + "/prices", null)
                                .body())
                        .get("errorCode")
                        .asString())
                .isEqualTo("prices_not_found");
        HttpResponse<String> events = analystB.send("GET", "/api/analyses/" + id + "/events", null);
        assertThat(events.statusCode()).isEqualTo(200);
        assertThat(events.body()).contains("event:end");

        for (String[] call : List.of(
                new String[] {"GET", "/logs", null},
                new String[] {"POST", "/stop", null},
                new String[] {"POST", "/rerun", null},
                new String[] {"PUT", "/visibility", "{\"visibility\":\"PRIVATE\",\"version\":" + version(seen) + "}"
                })) {
            HttpResponse<String> response = analystB.send(call[0], "/api/analyses/" + id + call[1], call[2]);

            assertError(response, 404, NOT_FOUND);
        }
        assertThat(get(admin, id).get("canManage").asBoolean()).isTrue();
    }

    @Test
    void makingItPrivateHidesItAgain() throws Exception {
        String id = startAndAwait(analystA, "SHRE");
        makePublic(id);
        assertThat(analystB.send("GET", "/api/analyses/" + id, null).statusCode())
                .isEqualTo(200);

        HttpResponse<String> response = setVisibility(analystA, id, PRIVATE, version(get(analystA, id)));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).get("visibility").asString()).isEqualTo(PRIVATE);
        assertError(analystB.send("GET", "/api/analyses/" + id, null), 404, NOT_FOUND);
        assertThat(ids(analystB.send("GET", "/api/analyses?scope=SHARED", null)))
                .doesNotContain(id);
    }

    @Test
    void anOwnerWhoLostAnalysisShareCanStillTakeItBack() throws Exception {
        String id = startAndAwait(analystA, "SHRF");
        makePublic(id);
        users.save(TestUsers.withRoleIds(
                users.findByUsername(new Username(analystAName)).orElseThrow(), Set.of(RoleId.ANALYST)));
        analystA = loggedInAs(analystAName);

        HttpResponse<String> back = setVisibility(analystA, id, PRIVATE, version(get(analystA, id)));
        assertThat(back.statusCode()).isEqualTo(200);

        HttpResponse<String> again = setVisibility(analystA, id, PUBLIC, version(get(analystA, id)));
        assertError(again, 403, "analysis_share_forbidden");
    }

    @Test
    void anAdminTakesAPublicAnalysisDownButCannotPublishForOthers() throws Exception {
        String id = startAndAwait(analystA, "SHRG");
        HttpResponse<String> publish = setVisibility(admin, id, PUBLIC, version(get(admin, id)));
        assertError(publish, 404, NOT_FOUND);

        makePublic(id);
        HttpResponse<String> takeDown = setVisibility(admin, id, PRIVATE, version(get(admin, id)));

        assertThat(takeDown.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(takeDown.body()).get("visibility").asString()).isEqualTo(PRIVATE);
        assertError(analystB.send("GET", "/api/analyses/" + id, null), 404, NOT_FOUND);
    }

    @Test
    void aStaleVersionChangesNothing() throws Exception {
        grantPublisher(analystAName);
        analystA = loggedInAs(analystAName);
        String id = startAndAwait(analystA, "SHRH");
        JsonNode current = get(analystA, id);

        HttpResponse<String> response = setVisibility(analystA, id, PUBLIC, version(current) + 1);

        assertError(response, 409, "concurrent_update");
        assertThat(get(analystA, id).get("visibility").asString()).isEqualTo(PRIVATE);
    }

    @Test
    void allListsEveryonesAnalysesForAnAdminAndTheOwnOnesOtherwise() throws Exception {
        String ofA = startAndAwait(analystA, "SHRI");
        String ofB = startAndAwait(analystB, "SHRJ");

        assertThat(ids(admin.send("GET", "/api/analyses?scope=ALL", null))).contains(ofA, ofB);
        assertThat(ids(analystA.send("GET", "/api/analyses?scope=ALL", null)))
                .contains(ofA)
                .doesNotContain(ofB);
        assertError(analystB.send("GET", "/api/analyses/" + ofA, null), 404, NOT_FOUND);
        assertThat(analystA.send("GET", "/api/analyses/" + ofA, null).statusCode())
                .isEqualTo(200);
        assertThat(admin.send("GET", "/api/analyses/" + ofA, null).statusCode()).isEqualTo(200);
    }

    @Test
    void aMissingVisibilityIsABadRequest() throws Exception {
        String id = startAndAwait(analystA, "SHRK");

        assertThat(analystA.send("PUT", "/api/analyses/" + id + "/visibility", "{\"version\":1}")
                        .statusCode())
                .isEqualTo(400);
        assertThat(analystA.send(
                                "PUT", "/api/analyses/" + id + "/visibility", "{\"visibility\":\"NOPE\",\"version\":1}")
                        .statusCode())
                .isEqualTo(400);
    }

    private void makePublic(String id) throws Exception {
        grantPublisher(analystAName);
        analystA = loggedInAs(analystAName);
        HttpResponse<String> response = setVisibility(analystA, id, PUBLIC, version(get(analystA, id)));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private void grantPublisher(String username) {
        User user = users.findByUsername(new Username(username)).orElseThrow();
        users.save(TestUsers.withRoleIds(user, Set.of(RoleId.ANALYST, RoleId.PUBLISHER)));
    }

    private OwnerId ownerOf(String username) {
        return new OwnerId(
                users.findByUsername(new Username(username)).orElseThrow().id().value());
    }

    private static HttpResponse<String> setVisibility(TestSession client, String id, String visibility, long version)
            throws Exception {
        return client.send(
                "PUT",
                "/api/analyses/" + id + "/visibility",
                "{\"visibility\":\"%s\",\"version\":%d}".formatted(visibility, version));
    }

    private static void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(JSON.readTree(response.body()).get("errorCode").asString()).isEqualTo(code);
    }

    private static JsonNode get(TestSession client, String id) throws Exception {
        HttpResponse<String> response = client.send("GET", "/api/analyses/" + id, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private static long version(JsonNode analysis) {
        return analysis.get("version").asLong();
    }

    private static AnalysisSpec spec(String ticker) {
        return new AnalysisSpec(
                ticker,
                LocalDate.of(2026, 9, 25),
                AssetType.STOCK,
                List.of(Analyst.MARKET),
                "deepseek",
                "deepseek-v4-pro",
                "deepseek-v4-flash",
                1,
                1,
                "English",
                true);
    }

    private String startAndAwait(TestSession client, String ticker) throws Exception {
        String body = """
                {"ticker":"%s","tradeDate":"2026-09-25","analysts":["MARKET"],"llmProvider":"deepseek",
                 "deepThinkLlm":"deepseek-v4-pro","quickThinkLlm":"deepseek-v4-flash"}""".formatted(ticker);
        HttpResponse<String> created = client.send("POST", "/api/analyses", body);
        assertThat(created.statusCode()).isEqualTo(201);
        String id = JSON.readTree(created.body()).get("id").asString();
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (get(client, id).get("status").asString().equals("COMPLETED")) {
                return id;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Analysis " + id + " never completed");
    }

    private static List<String> ids(HttpResponse<String> list) {
        assertThat(list.statusCode()).isEqualTo(200);
        return JSON.readTree(list.body())
                .valueStream()
                .map(a -> a.get("id").asString())
                .toList();
    }

    private TestSession loggedInAs(String username) throws Exception {
        TestSession client = TestSession.anonymous(port);
        assertThat(client.login(username, TestSession.PASSWORD).statusCode()).isEqualTo(200);
        return client;
    }
}
