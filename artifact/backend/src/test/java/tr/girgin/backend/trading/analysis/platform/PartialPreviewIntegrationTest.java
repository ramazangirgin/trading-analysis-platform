package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/**
 * Partial visibility through the API: who may make an analysis partial, what a visitor without a session reads
 * of it, and that nothing else is open to the visitor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
// Closes the context (and its database container) when the class is done: the container engine of a laptop
// does not keep one more PostgreSQL alive for the rest of the build.
@DirtiesContext
class PartialPreviewIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final Path RUNNER = HOME.resolve("runner.sh");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NOT_FOUND = "not_found";
    private static final String PUBLIC = "PUBLIC";
    private static final String PRIVATE = "PRIVATE";
    private static final String PARTIAL = "PARTIAL";
    private static final String PUBLIC_LIST = "/api/public/analyses";
    private static final String VISIBLE_TEXT = "VISIBLE-MARKET-TEXT";
    private static final List<String> HIDDEN_TEXTS =
            List.of("HIDDEN-SENTIMENT-TEXT", "HIDDEN-BULL-TEXT", "HIDDEN-DECISION-TEXT");

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

    private String ownerName;
    private String otherName;
    private TestSession owner;
    private TestSession other;
    private TestSession admin;
    private TestSession visitor;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        Files.writeString(RUNNER, SCRIPT);
        TestPlatformHome.register(registry, HOME, RUNNER);
        // Every request of this class comes from one address; the limit has tests of its own.
        registry.add("platform.public.rate-limit.max-requests", () -> "10000");
    }

    @BeforeEach
    void logIn() throws Exception {
        ownerName = TestSession.createUser(users, hasher, true, RoleId.ANALYST, RoleId.PUBLISHER);
        otherName = TestSession.createUser(users, hasher, true, RoleId.ANALYST);
        owner = loggedInAs(ownerName);
        other = loggedInAs(otherName);
        admin = loggedInAs(TestSession.createUser(users, hasher, true, RoleId.ADMIN));
        visitor = TestSession.anonymous(port);
    }

    @Test
    void theOwnerWithAnalysisShareMakesACompletedAnalysisPartial() throws Exception {
        String id = startAndAwait(owner, "PRVA");
        assertThat(allowed(get(owner, id))).containsExactly(PUBLIC, PARTIAL);

        HttpResponse<String> response = setVisibility(owner, id, PARTIAL, version(get(owner, id)));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode partial = JSON.readTree(response.body());
        assertThat(partial.get("visibility").asString()).isEqualTo(PARTIAL);
        assertThat(allowed(partial)).containsExactly(PRIVATE, PUBLIC);
        assertThat(partial.get("canChangeVisibility").asBoolean()).isTrue();
    }

    @Test
    void aRunningAnalysisCannotBeMadePartial() throws Exception {
        AnalysisId running = AnalysisId.newId();
        analyses.insert(Analysis.queued(running, spec("PRVB"), ownerId(ownerName), Instant.now()));
        analyses.update(analyses.findById(running).orElseThrow().running(Instant.now(), "ref"));

        HttpResponse<String> response =
                setVisibility(owner, running.value(), PARTIAL, version(get(owner, running.value())));

        assertError(response, 409, "analysis_not_shareable");
    }

    @Test
    void aUserWithoutAnalysisShareCannotMakeItPartial() throws Exception {
        String id = startAndAwait(other, "PRVC");

        HttpResponse<String> response = setVisibility(other, id, PARTIAL, version(get(other, id)));

        assertError(response, 403, "analysis_share_forbidden");
        assertThat(allowed(get(other, id))).isEmpty();
    }

    @Test
    void aVisitorReadsTheConfiguredPartsAndTheTitlesOfTheHiddenOnesOnly() throws Exception {
        String id = startAndAwait(owner, "PRVD");
        makePartial(id);

        HttpResponse<String> list = plain(PUBLIC_LIST);
        HttpResponse<String> preview = plain(PUBLIC_LIST + "/" + id);

        assertThat(list.statusCode()).isEqualTo(200);
        JsonNode card = JSON.readTree(list.body())
                .get("items")
                .valueStream()
                .filter(item -> item.get("id").asString().equals(id))
                .findFirst()
                .orElseThrow();
        assertThat(card.get("ticker").asString()).isEqualTo("PRVD");
        assertThat(card.get("excerpt").asString()).contains(VISIBLE_TEXT);
        assertThat(preview.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(preview.body());
        assertThat(body.get("ticker").asString()).isEqualTo("PRVD");
        assertThat(body.get("analysisDate").asString()).isEqualTo("2026-09-25");
        assertThat(body.get("rating").asString()).isEqualTo("HOLD");
        assertThat(body.at("/sections/market_report").asString()).contains(VISIBLE_TEXT);
        assertThat(body.get("sections").size()).isEqualTo(1);
        assertThat(body.get("lockedSections").valueStream().map(JsonNode::asString))
                .contains("sentiment_report", "final_trade_decision", "bull", "risk_judge")
                .doesNotContain("market_report");
        String ownerId = ownerId(ownerName).value();
        for (String raw : List.of(list.body(), preview.body())) {
            assertThat(raw).doesNotContain(HIDDEN_TEXTS).doesNotContain(ownerName, ownerId);
            assertThat(raw).doesNotContain("\"owner\"", "\"sources\"", "\"debates\"", "prices");
        }
    }

    @Test
    void aVisitorGets404ForPrivateAndPublicAnalysesAnd401ForEverythingElse() throws Exception {
        String privateId = startAndAwait(owner, "PRVE");
        String publicId = startAndAwait(owner, "PRVF");
        setVisibilityOk(owner, publicId, PUBLIC);

        for (String id : List.of(privateId, publicId)) {
            HttpResponse<String> response = plain(PUBLIC_LIST + "/" + id);
            assertError(response, 404, NOT_FOUND);
        }
        assertThat(plain(PUBLIC_LIST).body()).doesNotContain(privateId, publicId);
        assertError(plain(PUBLIC_LIST + "/not-an-id"), 404, NOT_FOUND);
        assertThat(plain(PUBLIC_LIST + "?size=25").statusCode()).isEqualTo(400);
        assertThat(plain(PUBLIC_LIST + "?page=-1").statusCode()).isEqualTo(400);
        assertThat(plain(PUBLIC_LIST + "?page=100000000").statusCode()).isEqualTo(400);

        for (String path : List.of(
                "/api/analyses",
                "/api/analyses/" + privateId,
                "/api/analyses/" + privateId + "/report",
                "/api/analyses/" + privateId + "/prices",
                "/api/analyses/" + privateId + "/events")) {
            assertThat(visitor.send("GET", path, null).statusCode()).as(path).isEqualTo(401);
        }
        assertThat(visitor.send("POST", PUBLIC_LIST, "{}").statusCode()).isEqualTo(401);
    }

    @Test
    void aLoggedInUserGetsTheSamePreviewAndReadsThePartialAnalysisInFull() throws Exception {
        String id = startAndAwait(owner, "PRVG");
        makePartial(id);

        HttpResponse<String> preview = other.send("GET", PUBLIC_LIST + "/" + id, null);

        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(preview.body()).get("sections").size()).isEqualTo(1);
        assertThat(ids(other.send("GET", "/api/analyses?scope=SHARED", null))).contains(id);
        JsonNode seen = get(other, id);
        assertThat(seen.get("visibility").asString()).isEqualTo(PARTIAL);
        assertThat(seen.get("canManage").asBoolean()).isFalse();
        assertThat(allowed(seen)).isEmpty();
        JsonNode report = JSON.readTree(
                other.send("GET", "/api/analyses/" + id + "/report", null).body());
        assertThat(report.at("/sections/sentiment_report").asString()).contains("HIDDEN-SENTIMENT-TEXT");
        for (String[] call : List.of(
                new String[] {"GET", "/logs"}, new String[] {"POST", "/stop"}, new String[] {"POST", "/rerun"})) {
            assertError(other.send(call[0], "/api/analyses/" + id + call[1], null), 404, NOT_FOUND);
        }
    }

    @Test
    void makingItPrivateHidesItFromVisitors() throws Exception {
        String id = startAndAwait(owner, "PRVH");
        makePartial(id);
        assertThat(plain(PUBLIC_LIST + "/" + id).statusCode()).isEqualTo(200);

        setVisibilityOk(owner, id, PRIVATE);

        assertThat(plain(PUBLIC_LIST).body()).doesNotContain(id);
        assertError(plain(PUBLIC_LIST + "/" + id), 404, NOT_FOUND);
    }

    @Test
    void anAdminMovesAPartialAnalysisToPublicOrPrivateButNeverMakesOneOfAnotherPartial() throws Exception {
        String id = startAndAwait(owner, "PRVI");
        assertError(setVisibility(admin, id, PARTIAL, version(get(admin, id))), 404, NOT_FOUND);
        makePartial(id);
        assertThat(allowed(get(admin, id))).containsExactly(PRIVATE, PUBLIC);

        JsonNode publicNow = setVisibilityOk(admin, id, PUBLIC);

        assertThat(allowed(publicNow)).containsExactly(PRIVATE);
        assertError(plain(PUBLIC_LIST + "/" + id), 404, NOT_FOUND);
        assertError(setVisibility(admin, id, PARTIAL, version(publicNow)), 404, NOT_FOUND);
        setVisibilityOk(admin, id, PRIVATE);
        assertError(other.send("GET", "/api/analyses/" + id, null), 404, NOT_FOUND);
    }

    private void makePartial(String id) throws Exception {
        setVisibilityOk(owner, id, PARTIAL);
    }

    private JsonNode setVisibilityOk(TestSession client, String id, String visibility) throws Exception {
        HttpResponse<String> response = setVisibility(client, id, visibility, version(get(client, id)));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private static List<String> allowed(JsonNode analysis) {
        return analysis.get("allowedVisibilities")
                .valueStream()
                .map(JsonNode::asString)
                .toList();
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

    private static List<String> ids(HttpResponse<String> list) {
        assertThat(list.statusCode()).isEqualTo(200);
        return JSON.readTree(list.body())
                .valueStream()
                .map(a -> a.get("id").asString())
                .toList();
    }

    private OwnerId ownerId(String username) {
        return new OwnerId(
                users.findByUsername(new Username(username)).orElseThrow().id().value());
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

    private static void writeReport(String ticker) throws IOException {
        Path reports =
                HOME.resolve("logs").resolve(ticker).resolve("2026-09-25").resolve("reports");
        Files.createDirectories(reports.resolve("1_analysts"));
        Files.createDirectories(reports.resolve("2_research"));
        Files.createDirectories(reports.resolve("5_portfolio"));
        Files.writeString(reports.resolve("1_analysts/market.md"), VISIBLE_TEXT + " of " + ticker);
        Files.writeString(reports.resolve("1_analysts/sentiment.md"), HIDDEN_TEXTS.get(0));
        Files.writeString(reports.resolve("2_research/bull.md"), HIDDEN_TEXTS.get(1));
        Files.writeString(reports.resolve("5_portfolio/decision.md"), HIDDEN_TEXTS.get(2));
    }

    private String startAndAwait(TestSession client, String ticker) throws Exception {
        writeReport(ticker);
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

    private TestSession loggedInAs(String username) throws Exception {
        TestSession client = TestSession.anonymous(port);
        assertThat(client.login(username, TestSession.PASSWORD).statusCode()).isEqualTo(200);
        return client;
    }

    // No cookies and no CSRF header: a visitor who has never opened the application.
    private HttpResponse<String> plain(String path) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
