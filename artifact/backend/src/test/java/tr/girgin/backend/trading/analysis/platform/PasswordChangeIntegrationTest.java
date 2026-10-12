package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/** The forced password change: the gate, the endpoint, and the logout of the user's other sessions. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class PasswordChangeIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PRESET = "{\"name\":\"Gate\",\"values\":{\"ticker\":\"NVDA\"}}";
    private static final String NEW_PASSWORD = "a brand new password";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private PasswordHasherPort hasher;

    @Autowired
    private SessionRegistry sessionRegistry;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestPlatformHome.register(registry, HOME, HOME.resolve("runner.sh"));
    }

    private TestSession loggedInAs(String username) throws Exception {
        TestSession session = TestSession.anonymous(port);
        assertThat(session.login(username, TestSession.PASSWORD).statusCode()).isEqualTo(200);
        return session;
    }

    private static String errorCode(HttpResponse<String> response) {
        return JSON.readTree(response.body()).get("errorCode").asString();
    }

    @Test
    void aFlaggedUserReachesNothingButMePasswordAndLogout() throws Exception {
        String username = TestSession.createUserWhoMustChangePassword(users, hasher);
        TestSession client = loggedInAs(username);

        for (String path : new String[] {"/api/analyses", "/api/presets"}) {
            HttpResponse<String> response = client.send("GET", path, null);
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(errorCode(response)).isEqualTo("password_change_required");
        }
        HttpResponse<String> unsafe = client.send("POST", "/api/presets", PRESET);
        assertThat(unsafe.statusCode()).isEqualTo(403);
        assertThat(errorCode(unsafe)).isEqualTo("password_change_required");

        HttpResponse<String> me = client.send("GET", "/api/auth/me", null);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(me.body()).get("mustChangePassword").asBoolean())
                .isTrue();
        assertThat(client.changePassword(TestSession.PASSWORD, NEW_PASSWORD).statusCode())
                .isEqualTo(200);

        TestSession other = loggedInAs(TestSession.createUserWhoMustChangePassword(users, hasher));
        assertThat(other.send("POST", "/api/auth/logout", null).statusCode()).isEqualTo(204);
    }

    @Test
    void anUnflaggedUserIsNotGated() throws Exception {
        TestSession client = TestSession.loggedIn(port, users, hasher);

        assertThat(client.send("GET", "/api/analyses", null).statusCode()).isEqualTo(200);
        assertThat(client.send("POST", "/api/presets", PRESET).statusCode()).isEqualTo(201);
    }

    @Test
    void aWrongCurrentPasswordIs400AndChangesNothing() throws Exception {
        String username = TestSession.createUserWhoMustChangePassword(users, hasher);
        TestSession client = loggedInAs(username);

        HttpResponse<String> response = client.changePassword("not the password", NEW_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(errorCode(response)).isEqualTo("wrong_current_password");
        assertThat(JSON.readTree(client.send("GET", "/api/auth/me", null).body())
                        .get("mustChangePassword")
                        .asBoolean())
                .isTrue();
        assertThat(users.findByUsername(new Username(username)).orElseThrow().mustChangePassword())
                .isTrue();
        assertThat(TestSession.anonymous(port)
                        .login(username, TestSession.PASSWORD)
                        .statusCode())
                .isEqualTo(200);
    }

    @Test
    void theSamePasswordIs400() throws Exception {
        TestSession client = loggedInAs(TestSession.createUserWhoMustChangePassword(users, hasher));

        HttpResponse<String> response = client.changePassword(TestSession.PASSWORD, TestSession.PASSWORD);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(errorCode(response)).isEqualTo("password_unchanged");
    }

    @Test
    void anEmptyFieldIs400() throws Exception {
        TestSession client = TestSession.loggedIn(port, users, hasher);

        for (HttpResponse<String> response :
                List.of(client.changePassword("", NEW_PASSWORD), client.changePassword(TestSession.PASSWORD, ""))) {
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(errorCode(response)).isEqualTo("invalid_request");
        }
    }

    @Test
    void withoutASessionIs401AndWithoutTheCsrfHeaderIs403() throws Exception {
        TestSession anonymous = TestSession.anonymous(port);
        HttpResponse<String> unauthorized = anonymous.changePassword(TestSession.PASSWORD, NEW_PASSWORD);
        assertThat(unauthorized.statusCode()).isEqualTo(401);
        assertThat(errorCode(unauthorized)).isEqualTo("unauthorized");

        TestSession client = TestSession.loggedIn(port, users, hasher);
        HttpResponse<String> noCsrf = client.sendWithoutCsrfHeader(
                "POST",
                "/api/auth/password",
                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(TestSession.PASSWORD, NEW_PASSWORD));
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(errorCode(noCsrf)).isEqualTo("invalid_csrf_token");
    }

    @Test
    void aChangeClearsTheFlagAndKeepsTheSessionWorkingUnderNewIds() throws Exception {
        String username = TestSession.createUserWhoMustChangePassword(users, hasher);
        TestSession client = loggedInAs(username);
        String sessionBefore = client.cookie(TestSession.SESSION_COOKIE);
        String csrfBefore = client.cookie(TestSession.CSRF_COOKIE);

        HttpResponse<String> response = client.changePassword(TestSession.PASSWORD, NEW_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).get("mustChangePassword").asBoolean())
                .isFalse();
        assertThat(client.send("GET", "/api/analyses", null).statusCode()).isEqualTo(200);
        assertThat(client.cookie(TestSession.SESSION_COOKIE)).isNotNull().isNotEqualTo(sessionBefore);
        assertThat(client.cookie(TestSession.CSRF_COOKIE)).isNotNull().isNotEqualTo(csrfBefore);
        JsonNode me = JSON.readTree(client.send("GET", "/api/auth/me", null).body());
        assertThat(me.get("mustChangePassword").asBoolean()).isFalse();
        assertThat(users.findByUsername(new Username(username)).orElseThrow().mustChangePassword())
                .isFalse();
        assertThat(client.send("POST", "/api/presets", PRESET).statusCode()).isEqualTo(201);

        TestSession fresh = TestSession.anonymous(port);
        assertThat(fresh.login(username, TestSession.PASSWORD).statusCode()).isEqualTo(401);
        assertThat(fresh.login(username, NEW_PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void aChangeEndsTheOtherSessionsOfTheUserOnly() throws Exception {
        String username = TestSession.createUser(users, hasher, true);
        TestSession first = loggedInAs(username);
        TestSession second = loggedInAs(username);
        TestSession stranger = TestSession.loggedIn(port, users, hasher);
        assertThat(second.send("GET", "/api/analyses", null).statusCode()).isEqualTo(200);

        assertThat(first.changePassword(TestSession.PASSWORD, NEW_PASSWORD).statusCode())
                .isEqualTo(200);

        HttpResponse<String> ended = second.send("GET", "/api/analyses", null);
        assertThat(ended.statusCode()).isEqualTo(401);
        assertThat(errorCode(ended)).isEqualTo("unauthorized");
        assertThat(first.send("GET", "/api/analyses", null).statusCode()).isEqualTo(200);
        assertThat(stranger.send("GET", "/api/analyses", null).statusCode()).isEqualTo(200);
    }

    @Test
    void theRegistryFollowsTheSessionIdOfALoginAndOfAChange() throws Exception {
        String username = TestSession.createUser(users, hasher, true);
        User stored = users.findByUsername(new Username(username)).orElseThrow();
        TestSession client = loggedInAs(username);
        // A second login in the same session changes its ID again.
        assertThat(client.login(username, TestSession.PASSWORD).statusCode()).isEqualTo(200);
        assertThat(sessionIds(stored)).containsExactly(client.cookie(TestSession.SESSION_COOKIE));

        assertThat(client.changePassword(TestSession.PASSWORD, NEW_PASSWORD).statusCode())
                .isEqualTo(200);

        assertThat(sessionIds(stored)).containsExactly(client.cookie(TestSession.SESSION_COOKIE));
        assertThat(client.changePassword(NEW_PASSWORD, TestSession.PASSWORD).statusCode())
                .isEqualTo(200);
        assertThat(client.send("GET", "/api/analyses", null).statusCode()).isEqualTo(200);
    }

    private List<String> sessionIds(User user) {
        return sessionRegistry.getAllSessions(user.id().value(), false).stream()
                .map(SessionInformation::getSessionId)
                .toList();
    }
}
