package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;
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
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/** The platform analysis defaults over HTTP, on a migrated PostgreSQL database and a runner that serves a catalog. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class AnalysisDefaultsApiIntegrationTest {

    private static final String PATH = "/api/settings/analysis-defaults";
    private static final Path HOME = TestPlatformHome.create();
    private static final Path RUNNER = HOME.resolve("runner.sh");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @SuppressWarnings("checkstyle:LineLength") // a catalog fixture stays on one line
    private static final String SCRIPT = """
            #!/bin/sh
            if [ "$1" = "catalog" ]; then
            cat <<'JSON'
            {"upstream_version":"0.5.1","defaults":{"llm_provider":"alpha","deep_think_llm":"alpha-deep","quick_think_llm":"alpha-quick"},"providers":[{"id":"alpha","api_key_env":"ALPHA_API_KEY","custom_model_allowed":false,"quick_models":[{"id":"alpha-quick","label":"Alpha quick"}],"deep_models":[{"id":"alpha-deep","label":"Alpha deep"},{"id":"alpha-deeper","label":"Alpha deeper"}]},{"id":"beta","api_key_env":null,"custom_model_allowed":false,"quick_models":[{"id":"beta-quick","label":"Beta quick"}],"deep_models":[{"id":"beta-deep","label":"Beta deep"}]}],"analysts":[{"id":"market","agent":"Market Analyst"}],"asset_types":["stock"]}
            JSON
            exit 0
            fi
            exit 1
            """;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private RoleRepositoryPort roles;

    @Autowired
    private PasswordHasherPort hasher;

    @Autowired
    private DataSource dataSource;

    private TestSession admin;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        Files.writeString(RUNNER, SCRIPT);
        TestPlatformHome.register(registry, HOME, RUNNER);
    }

    @BeforeEach
    void startFromAnEmptyTable() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM \"SETTINGS\".\"ANALYSIS_DEFAULTS\"");
        }
        admin = TestSession.loggedIn(port, users, hasher, RoleId.ADMIN);
    }

    @Test
    void whileNothingIsStoredTheCatalogDefaultsAndOneRoundEachAreAnswered() throws Exception {
        HttpResponse<String> response = admin.send("GET", PATH, null);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode defaults = JSON.readTree(response.body());
        assertThat(defaults.get("llmProvider").asString()).isEqualTo("alpha");
        assertThat(defaults.get("deepThinkLlm").asString()).isEqualTo("alpha-deep");
        assertThat(defaults.get("quickThinkLlm").asString()).isEqualTo("alpha-quick");
        assertThat(defaults.get("maxDebateRounds").asInt()).isEqualTo(1);
        assertThat(defaults.get("maxRiskDiscussRounds").asInt()).isEqualTo(1);
        assertThat(defaults.get("version").isNull()).isTrue();
    }

    @Test
    void theFirstSaveWithoutAVersionStoresTheValuesAtVersionZero() throws Exception {
        HttpResponse<String> saved = admin.send("PUT", PATH, body("beta", "beta-deep", "beta-quick", 3, 2, null));

        assertThat(saved.statusCode()).isEqualTo(200);
        JsonNode defaults = JSON.readTree(saved.body());
        assertThat(defaults.get("llmProvider").asString()).isEqualTo("beta");
        assertThat(defaults.get("maxDebateRounds").asInt()).isEqualTo(3);
        assertThat(defaults.get("version").asLong()).isZero();

        JsonNode read = JSON.readTree(admin.send("GET", PATH, null).body());
        assertThat(read.get("llmProvider").asString()).isEqualTo("beta");
        assertThat(read.get("maxRiskDiscussRounds").asInt()).isEqualTo(2);
        assertThat(read.get("version").asLong()).isZero();
    }

    @Test
    void theSecondOfTwoAdminsWhoReadNothingStoredGetsAConflictAndTheFirstValuesStay() throws Exception {
        TestSession other = TestSession.loggedIn(port, users, hasher, RoleId.ADMIN);
        assertThat(JSON.readTree(admin.send("GET", PATH, null).body())
                        .get("version")
                        .isNull())
                .isTrue();
        assertThat(JSON.readTree(other.send("GET", PATH, null).body())
                        .get("version")
                        .isNull())
                .isTrue();

        assertThat(admin.send("PUT", PATH, body("alpha", "alpha-deep", "alpha-quick", 2, 2, null))
                        .statusCode())
                .isEqualTo(200);
        HttpResponse<String> second = other.send("PUT", PATH, body("beta", "beta-deep", "beta-quick", 5, 5, null));

        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(errorCode(second)).isEqualTo("concurrent_update");
        JsonNode stored = JSON.readTree(admin.send("GET", PATH, null).body());
        assertThat(stored.get("llmProvider").asString()).isEqualTo("alpha");
        assertThat(stored.get("maxDebateRounds").asInt()).isEqualTo(2);
    }

    @Test
    void aSaveWithAStaleVersionIsAConflictAndAFreshOneRaisesTheVersion() throws Exception {
        admin.send("PUT", PATH, body("alpha", "alpha-deep", "alpha-quick", 1, 1, null));
        HttpResponse<String> first = admin.send("PUT", PATH, body("alpha", "alpha-deeper", "alpha-quick", 1, 1, 0L));
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(first.body()).get("version").asLong()).isEqualTo(1);

        HttpResponse<String> stale = admin.send("PUT", PATH, body("beta", "beta-deep", "beta-quick", 1, 1, 0L));

        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(errorCode(stale)).isEqualTo("concurrent_update");
        JsonNode stored = JSON.readTree(admin.send("GET", PATH, null).body());
        assertThat(stored.get("deepThinkLlm").asString()).isEqualTo("alpha-deeper");
        assertThat(stored.get("version").asLong()).isEqualTo(1);
    }

    @Test
    void valuesTheCatalogDoesNotOfferAreRefusedWithTheirField() throws Exception {
        assertInvalid(body("gamma", "alpha-deep", "alpha-quick", 1, 1, null), "llmProvider");
        assertInvalid(body("alpha", "beta-deep", "alpha-quick", 1, 1, null), "deepThinkLlm");
        assertInvalid(body("alpha", "alpha-deep", "alpha-deep", 1, 1, null), "quickThinkLlm");
        assertInvalid(body("alpha", "alpha-deep", "alpha-quick", 0, 1, null), "maxDebateRounds");
        assertInvalid(body("alpha", "alpha-deep", "alpha-quick", 1, 11, null), "maxRiskDiscussRounds");

        assertThat(JSON.readTree(admin.send("GET", PATH, null).body())
                        .get("version")
                        .isNull())
                .isTrue();
    }

    @Test
    void aRoleWithOnlyTheManagePermissionReadsAndSavesTheDefaults() throws Exception {
        RoleId role = new RoleId(UUID.randomUUID().toString());
        roles.save(new Role(
                role, "test-" + role.value(), false, "test role", Set.of(Permission.ANALYSIS_DEFAULTS_MANAGE), null));
        TestSession manager = TestSession.loggedIn(port, users, hasher, role);

        assertThat(manager.send("GET", PATH, null).statusCode()).isEqualTo(200);
        assertThat(manager.send("PUT", PATH, body("alpha", "alpha-deep", "alpha-quick", 1, 1, null))
                        .statusCode())
                .isEqualTo(200);
    }

    private void assertInvalid(String body, String field) throws Exception {
        HttpResponse<String> response = admin.send("PUT", PATH, body);

        assertThat(response.statusCode()).as(field).isEqualTo(400);
        JsonNode error = JSON.readTree(response.body());
        assertThat(error.get("errorCode").asString()).as(field).isEqualTo("invalid_analysis_defaults");
        assertThat(error.get("params").get("field").asString()).as(field).isEqualTo(field);
    }

    private static String errorCode(HttpResponse<String> response) {
        return JSON.readTree(response.body()).get("errorCode").asString();
    }

    private static String body(
            String provider, String deep, String quick, int debateRounds, int riskRounds, Long version) {
        return "{\"llmProvider\":\"%s\",\"deepThinkLlm\":\"%s\",\"quickThinkLlm\":\"%s\",\"maxDebateRounds\":%d,"
                        .formatted(provider, deep, quick, debateRounds)
                + "\"maxRiskDiscussRounds\":%d".formatted(riskRounds)
                + (version == null ? "" : ",\"version\":" + version) + "}";
    }
}
