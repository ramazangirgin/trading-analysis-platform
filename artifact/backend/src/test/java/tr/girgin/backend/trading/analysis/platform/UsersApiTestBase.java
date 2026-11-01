package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/**
 * What the user management tests share: a running platform (one context for the subclasses, with a shell script in
 * place of ta-runner) and helpers to create users, log them in and read the answers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
abstract class UsersApiTestBase {

    static final String ENTERED_PASSWORD = "an entered one-time passphrase";

    private static final Path HOME = TestPlatformHome.create();
    private static final Path RUNNER = HOME.resolve("runner.sh");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    // A run that takes three seconds, so a stream of it is still open when its session ends.
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
            sleep 3
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
    private RoleRepositoryPort roles;

    @Autowired
    private PasswordHasherPort hasher;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        Files.writeString(RUNNER, SCRIPT);
        TestPlatformHome.register(registry, HOME, RUNNER);
    }

    int port() {
        return port;
    }

    UserRepositoryPort users() {
        return users;
    }

    // A stored enabled user with the given roles and TestSession.PASSWORD.
    Account newAccount(RoleId... roleIds) {
        String username = TestSession.createUser(users, hasher, true, roleIds);
        return new Account(
                username,
                users.findByUsername(new Username(username)).orElseThrow().id().value());
    }

    TestSession login(Account account) throws Exception {
        return login(account.username(), TestSession.PASSWORD);
    }

    TestSession login(String username, String password) throws Exception {
        TestSession session = TestSession.anonymous(port);
        assertThat(session.login(username, password).statusCode()).isEqualTo(200);
        return session;
    }

    TestSession admin() throws Exception {
        return login(newAccount(RoleId.ADMIN));
    }

    // A logged-in user whose only role holds exactly the given permissions.
    TestSession userWithPermissions(Permission... permissions) throws Exception {
        RoleId role = new RoleId(UUID.randomUUID().toString());
        roles.save(new Role(role, "test-" + role.value(), false, "test role", Set.of(permissions), null));
        return login(newAccount(role));
    }

    // Locks the account until the given time, as failed logins do.
    void lock(Account account, Instant until) {
        User user = users.findByUsername(new Username(account.username())).orElseThrow();
        users.save(TestUsers.withFailedLogin(user, 5, Optional.of(until)));
    }

    JsonNode getUser(TestSession session, String id) throws Exception {
        HttpResponse<String> response = session.send("GET", "/api/users/" + id, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return body(response);
    }

    static JsonNode body(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    static String errorCode(HttpResponse<String> response) {
        return body(response).get("errorCode").asString();
    }

    static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asString();
    }

    static List<String> texts(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }

    static List<String> roleNames(JsonNode user) {
        return user.get("roles")
                .valueStream()
                .map(r -> r.get("name").asString())
                .toList();
    }

    // A JSON array of role IDs.
    static String ids(RoleId... roleIds) {
        return Stream.of(roleIds).map(id -> "\"" + id.value() + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    static String createBody(String username, String roleIds, String password) {
        String passwordField = password == null ? "" : ",\"password\":\"" + password + "\"";
        return "{\"username\":\"%s\",\"roleIds\":%s%s}".formatted(username, roleIds, passwordField);
    }

    static String newUsername() {
        return "new-" + UUID.randomUUID();
    }

    record Account(String username, String id) {}
}
