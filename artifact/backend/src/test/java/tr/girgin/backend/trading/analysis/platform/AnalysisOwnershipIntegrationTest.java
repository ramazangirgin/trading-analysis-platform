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
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserDirectoryUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;
import tr.girgin.backend.trading.analysis.platform.orchestration.ownership.inbound.AssignAnalysisOwnersUseCase;

/** Who sees which analysis: an analyst only their own, an admin everyone's, and the unowned go to the first admin. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class AnalysisOwnershipIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final Path RUNNER = HOME.resolve("runner.sh");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NOT_FOUND = "not_found";

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
    private UserDirectoryUseCase userDirectory;

    @Autowired
    private AssignAnalysisOwnersUseCase assignOwners;

    @Autowired
    private DataSource dataSource;

    private String analystAName;
    private String analystBName;
    private String adminName;
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
        adminName = TestSession.createUser(users, hasher, true, RoleId.ADMIN);
        analystA = loggedInAs(analystAName);
        analystB = loggedInAs(analystBName);
        admin = loggedInAs(adminName);
    }

    @Test
    void anAnalystDoesNotSeeAnotherUsersAnalysis() throws Exception {
        String id = startAndAwait(analystB, "OWNB");
        String own = startAndAwait(analystA, "OWNA");

        assertThat(ids(analystA.send("GET", "/api/analyses", null)))
                .contains(own)
                .doesNotContain(id);
        assertThat(ids(analystA.send("GET", "/api/analyses?allUsers=true", null)))
                .contains(own)
                .doesNotContain(id);
        assertThat(ids(analystB.send("GET", "/api/analyses", null)))
                .contains(id)
                .doesNotContain(own);

        for (String[] call : List.of(
                new String[] {"GET", ""},
                new String[] {"GET", "/report"},
                new String[] {"GET", "/prices"},
                new String[] {"GET", "/logs"},
                new String[] {"GET", "/events"},
                new String[] {"POST", "/stop"},
                new String[] {"POST", "/rerun"})) {
            HttpResponse<String> response = analystA.send(call[0], "/api/analyses/" + id + call[1], null);

            assertThat(response.statusCode()).as(call[0] + " " + call[1]).isEqualTo(404);
            assertThat(JSON.readTree(response.body()).get("errorCode").asString())
                    .isEqualTo(NOT_FOUND);
        }
        assertThat(analystB.send("GET", "/api/analyses/" + id, null).statusCode())
                .isEqualTo(200);
    }

    @Test
    void anAnalysisShowsItsOwner() throws Exception {
        String id = startAndAwait(analystA, "OWNC");

        JsonNode owned =
                JSON.readTree(analystA.send("GET", "/api/analyses/" + id, null).body());

        assertThat(owned.at("/owner/username").asString()).isEqualTo(analystAName);
        assertThat(owned.at("/owner/id").asString()).isNotBlank();
    }

    @Test
    void anAdminSeesEveryonesAnalysesOnRequestAndOwnOtherwise() throws Exception {
        String id = startAndAwait(analystB, "OWND");

        assertThat(ids(admin.send("GET", "/api/analyses", null))).doesNotContain(id);
        JsonNode all = JSON.readTree(
                admin.send("GET", "/api/analyses?allUsers=true", null).body());
        JsonNode theirs = all.valueStream()
                .filter(a -> a.get("id").asString().equals(id))
                .findFirst()
                .orElseThrow();
        assertThat(theirs.at("/owner/username").asString()).isEqualTo(analystBName);

        assertThat(admin.send("GET", "/api/analyses/" + id, null).statusCode()).isEqualTo(200);
        assertThat(admin.send("GET", "/api/analyses/" + id + "/logs", null).statusCode())
                .isEqualTo(200);
        // The run is visible but has no report files: that is the report's own 404, not the access one.
        HttpResponse<String> report = admin.send("GET", "/api/analyses/" + id + "/report", null);
        assertThat(JSON.readTree(report.body()).get("errorCode").asString()).isEqualTo("report_not_found");
        HttpResponse<String> prices = admin.send("GET", "/api/analyses/" + id + "/prices", null);
        assertThat(JSON.readTree(prices.body()).get("errorCode").asString()).isEqualTo("prices_not_found");
        HttpResponse<String> events = admin.send("GET", "/api/analyses/" + id + "/events", null);
        assertThat(events.statusCode()).isEqualTo(200);
        assertThat(events.body()).contains("event:end");
    }

    @Test
    void analysesWithoutAnOwnerGoToTheFirstAdmin() throws Exception {
        AnalysisId unowned = AnalysisId.newId();
        analyses.insert(Analysis.queued(unowned, spec("OWNE"), null, Instant.now()));
        String firstAdmin = userDirectory.firstAdmin().orElseThrow().value();

        assignOwners.assignUnownedToFirstAdmin();

        JsonNode all = JSON.readTree(
                admin.send("GET", "/api/analyses?allUsers=true", null).body());
        assertThat(all.valueStream())
                .allSatisfy(a -> assertThat(a.at("/owner/id").asString()).isNotBlank());
        JsonNode seeded = all.valueStream()
                .filter(a -> a.get("id").asString().equals(unowned.value()))
                .findFirst()
                .orElseThrow();
        assertThat(seeded.at("/owner/id").asString()).isEqualTo(firstAdmin);
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
            JsonNode analysis = JSON.readTree(
                    client.send("GET", "/api/analyses/" + id, null).body());
            if (analysis.get("status").asString().equals("COMPLETED")) {
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
