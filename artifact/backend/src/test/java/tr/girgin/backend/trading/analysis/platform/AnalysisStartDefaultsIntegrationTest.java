package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
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
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class AnalysisStartDefaultsIntegrationTest {

    private static final String DEFAULTS_PATH = "/api/settings/analysis-defaults";
    private static final Path HOME = TestPlatformHome.create();
    private static final Path RUNNER = HOME.resolve("runner.sh");
    private static final JsonMapper JSON = JsonMapper.builder().build();

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
            line() { printf '{"v":1,"ts":"2026-09-29T10:00:0%sZ","run_id":"x","seq":%s,"type":"%s"%s}\\n' "$1" "$1" "$2" "$3" | tee -a "$OUT/events.jsonl"; }
            line 1 run_started ',"spec":{}'
            line 2 run_finished ',"status":"completed","report_dir":"/tmp"'
            """;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private PasswordHasherPort hasher;

    @Autowired
    private DataSource dataSource;

    private TestSession analyst;
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
        analyst = TestSession.loggedIn(port, users, hasher, RoleId.ANALYST);
        admin = TestSession.loggedIn(port, users, hasher, RoleId.ADMIN);
    }

    @Test
    void anAnalystWithoutTheFiveFieldsGetsTheCatalogDefaultsAndOneRoundEach() throws Exception {
        HttpResponse<String> created = analyst.send("POST", "/api/analyses", withoutModels("NODF"));

        assertThat(created.statusCode()).isEqualTo(201);
        assertSpec(JSON.readTree(created.body()), "deepseek", "deepseek-v4-pro", "deepseek-v4-flash", 1, 1);
        awaitCompleted(analyst, JSON.readTree(created.body()).get("id").asString());
    }

    @Test
    void afterAnAdminSavedDefaultsAnAnalystsRunUsesThemAndADifferingRequestIsForbidden() throws Exception {
        saveDefaults(3, 2);

        HttpResponse<String> withoutFields = analyst.send("POST", "/api/analyses", withoutModels("SVDF"));
        HttpResponse<String> samePlatformValues = analyst.send("POST", "/api/analyses", request("SVSM", "deepseek"));
        HttpResponse<String> other = analyst.send("POST", "/api/analyses", request("SVOT", "openai"));

        assertThat(withoutFields.statusCode()).isEqualTo(201);
        assertSpec(JSON.readTree(withoutFields.body()), "deepseek", "deepseek-v4-pro", "deepseek-v4-flash", 3, 2);
        assertThat(samePlatformValues.statusCode()).isEqualTo(201);
        assertThat(other.statusCode()).isEqualTo(403);
        JsonNode error = JSON.readTree(other.body());
        assertThat(error.get("errorCode").asString()).isEqualTo("analysis_override_forbidden");
        assertThat(error.at("/params/field").asString()).isEqualTo("llmProvider");
        awaitCompleted(analyst, JSON.readTree(withoutFields.body()).get("id").asString());
        awaitCompleted(
                analyst, JSON.readTree(samePlatformValues.body()).get("id").asString());
    }

    @Test
    void anAdminsOverrideAppliesToThatRunOnly() throws Exception {
        saveDefaults(3, 2);
        String before = admin.send("GET", DEFAULTS_PATH, null).body();

        HttpResponse<String> created = admin.send("POST", "/api/analyses", """
                {"ticker":"ADOV","tradeDate":"2026-09-25","analysts":["MARKET"],
                 "deepThinkLlm":"deepseek-custom"}""");

        assertThat(created.statusCode()).isEqualTo(201);
        assertSpec(JSON.readTree(created.body()), "deepseek", "deepseek-custom", "deepseek-v4-flash", 3, 2);
        assertThat(admin.send("GET", DEFAULTS_PATH, null).body()).isEqualTo(before);
        awaitCompleted(admin, JSON.readTree(created.body()).get("id").asString());
    }

    @Test
    void anAdminsOverrideTheCatalogRefusesIsAnInvalidSpec() throws Exception {
        HttpResponse<String> response = admin.send("POST", "/api/analyses", request("ADBAD", "nope"));

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode error = JSON.readTree(response.body());
        assertThat(error.get("errorCode").asString()).isEqualTo("invalid_spec");
        assertThat(error.at("/params/field").asString()).isEqualTo("llmProvider");
    }

    @Test
    void anAnalystsRerunKeepsTheOriginalValuesAfterTheDefaultsChanged() throws Exception {
        saveDefaults(3, 2);
        String id = JSON.readTree(analyst.send("POST", "/api/analyses", withoutModels("RRDF"))
                        .body())
                .get("id")
                .asString();
        awaitCompleted(analyst, id);
        saveDefaults(5, 4);

        HttpResponse<String> rerun = analyst.send("POST", "/api/analyses/" + id + "/rerun", null);

        assertThat(rerun.statusCode()).isEqualTo(201);
        assertSpec(JSON.readTree(rerun.body()), "deepseek", "deepseek-v4-pro", "deepseek-v4-flash", 3, 2);
        awaitCompleted(analyst, JSON.readTree(rerun.body()).get("id").asString());
    }

    private void saveDefaults(int debateRounds, int riskRounds) throws Exception {
        String version = JSON.readTree(admin.send("GET", DEFAULTS_PATH, null).body())
                .get("version")
                .toString();
        String body = """
                {"llmProvider":"deepseek","deepThinkLlm":"deepseek-v4-pro","quickThinkLlm":"deepseek-v4-flash",
                 "maxDebateRounds":%d,"maxRiskDiscussRounds":%d,"version":%s}""";
        HttpResponse<String> saved =
                admin.send("PUT", DEFAULTS_PATH, body.formatted(debateRounds, riskRounds, version));
        assertThat(saved.statusCode()).isEqualTo(200);
    }

    private static String withoutModels(String ticker) {
        return """
                {"ticker":"%s","tradeDate":"2026-09-25","analysts":["MARKET"]}""".formatted(ticker);
    }

    private static String request(String ticker, String provider) {
        return """
                {"ticker":"%s","tradeDate":"2026-09-25","analysts":["MARKET"],"llmProvider":"%s",
                 "deepThinkLlm":"deepseek-v4-pro","quickThinkLlm":"deepseek-v4-flash",
                 "maxDebateRounds":3,"maxRiskDiscussRounds":2}""".formatted(ticker, provider);
    }

    private static void assertSpec(
            JsonNode analysis, String provider, String deep, String quick, int debateRounds, int riskRounds) {
        assertThat(analysis.at("/spec/llmProvider").asString()).isEqualTo(provider);
        assertThat(analysis.at("/spec/deepThinkLlm").asString()).isEqualTo(deep);
        assertThat(analysis.at("/spec/quickThinkLlm").asString()).isEqualTo(quick);
        assertThat(analysis.at("/spec/maxDebateRounds").asInt()).isEqualTo(debateRounds);
        assertThat(analysis.at("/spec/maxRiskDiscussRounds").asInt()).isEqualTo(riskRounds);
    }

    private static void awaitCompleted(TestSession client, String id) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode analysis = JSON.readTree(
                    client.send("GET", "/api/analyses/" + id, null).body());
            if (analysis.get("status").asString().equals("COMPLETED")) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Analysis " + id + " never completed");
    }
}
