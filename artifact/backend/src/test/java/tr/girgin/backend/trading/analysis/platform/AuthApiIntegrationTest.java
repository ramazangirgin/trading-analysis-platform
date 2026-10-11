package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/** Login, logout, CSRF and the 401 rule over HTTP, with {@code platform.auth.enabled=true}. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class AuthApiIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PRESET = "{\"name\":\"Auth\",\"values\":{\"ticker\":\"NVDA\"}}";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private PasswordHasherPort hasher;

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("platform.auth.enabled", () -> "true");
        TestPlatformHome.register(registry, HOME, HOME.resolve("runner.sh"));
    }

    @Test
    void answers401WithoutASessionAndNeverRedirects() throws Exception {
        TestSession anonymous = TestSession.anonymous(port);

        HttpResponse<String> response = anonymous.send("GET", "/api/analyses", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(response.body()).get("errorCode").asString()).isEqualTo("unauthorized");
        assertThat(response.headers().firstValue("Location")).isEmpty();
    }

    @Test
    void meWithoutASessionIs401AndSetsTheCsrfCookie() throws Exception {
        TestSession anonymous = TestSession.anonymous(port);

        HttpResponse<String> response = anonymous.send("GET", "/api/auth/me", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(response.body()).get("errorCode").asString()).isEqualTo("unauthorized");
        assertThat(anonymous.cookie(TestSession.CSRF_COOKIE)).isNotBlank();
        assertThat(anonymous.cookie(TestSession.SESSION_COOKIE)).isNull();
    }

    @Test
    void aLoginWithoutTheCsrfHeaderIs403() throws Exception {
        String username = TestSession.createUser(users, hasher, true);
        TestSession anonymous = TestSession.anonymous(port);

        HttpResponse<String> response = anonymous.sendWithoutCsrfHeader(
                "POST",
                "/api/auth/login",
                "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, TestSession.PASSWORD));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(response.body()).get("errorCode").asString()).isEqualTo("invalid_csrf_token");
    }

    @Test
    void wrongPasswordUnknownUserAndDisabledUserAreTheSame401() throws Exception {
        String active = TestSession.createUser(users, hasher, true);
        String disabled = TestSession.createUser(users, hasher, false);
        TestSession anonymous = TestSession.anonymous(port);

        HttpResponse<String> wrongPassword = anonymous.login(active, "not the password");
        HttpResponse<String> unknown = anonymous.login("nobody-" + active, TestSession.PASSWORD);
        HttpResponse<String> disabledUser = anonymous.login(disabled, TestSession.PASSWORD);

        for (HttpResponse<String> response : java.util.List.of(wrongPassword, unknown, disabledUser)) {
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(JSON.readTree(response.body()).get("errorCode").asString())
                    .isEqualTo("bad_credentials");
        }
        assertThat(unknown.body()).isEqualTo(wrongPassword.body());
        assertThat(disabledUser.body()).isEqualTo(wrongPassword.body());
    }

    @Test
    void aValidLoginStartsAProtectedSessionWithAFreshId() throws Exception {
        String username = TestSession.createUser(users, hasher, true);
        TestSession client = TestSession.anonymous(port);

        HttpResponse<String> login = client.login(username, TestSession.PASSWORD);

        assertThat(login.statusCode()).isEqualTo(200);
        JsonNode user = JSON.readTree(login.body());
        assertThat(user.get("username").asString()).isEqualTo(username);
        assertThat(user.get("roles").isArray()).isTrue();
        assertThat(user.get("permissions").isArray()).isTrue();
        assertThat(user.get("mustChangePassword").asBoolean()).isFalse();
        String sessionCookie = login.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(TestSession.SESSION_COOKIE + "="))
                .findFirst()
                .orElseThrow();
        assertThat(sessionCookie).contains("HttpOnly").containsIgnoringCase("SameSite=Lax");

        HttpResponse<String> me = client.send("GET", "/api/auth/me", null);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(me.body())).isEqualTo(user);

        // Session fixation: a login that arrives with a session gets another ID.
        String before = client.cookie(TestSession.SESSION_COOKIE);
        assertThat(client.login(username, TestSession.PASSWORD).statusCode()).isEqualTo(200);
        assertThat(client.cookie(TestSession.SESSION_COOKIE)).isNotNull().isNotEqualTo(before);
    }

    @Test
    void anUnsafeRequestWithASessionNeedsTheCsrfToken() throws Exception {
        TestSession client = TestSession.loggedIn(port, users, hasher);

        HttpResponse<String> without = client.sendWithoutCsrfHeader("POST", "/api/presets", PRESET);
        HttpResponse<String> with = client.send("POST", "/api/presets", PRESET);

        assertThat(without.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(without.body()).get("errorCode").asString()).isEqualTo("invalid_csrf_token");
        assertThat(with.statusCode()).isEqualTo(201);
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        TestSession client = TestSession.loggedIn(port, users, hasher);
        String sessionId = client.cookie(TestSession.SESSION_COOKIE);
        assertThat(client.send("GET", "/api/analyses", null).statusCode()).isEqualTo(200);

        HttpResponse<String> logout = client.send("POST", "/api/auth/logout", null);

        assertThat(logout.statusCode()).isEqualTo(204);
        // A client that still holds the old cookie is not logged in.
        TestSession replay = TestSession.anonymous(port);
        replay.setCookie(TestSession.SESSION_COOKIE, sessionId);
        assertThat(replay.send("GET", "/api/analyses", null).statusCode()).isEqualTo(401);
        assertThat(client.send("GET", "/api/analyses", null).statusCode()).isEqualTo(401);
    }

    @Test
    void logoutWithoutASessionIs204() throws Exception {
        TestSession anonymous = TestSession.anonymous(port);

        assertThat(anonymous.send("POST", "/api/auth/logout", null).statusCode())
                .isEqualTo(204);
    }

    @Test
    void staticAssetsClientRoutesActuatorAndOpenApiNeedNoSession() throws Exception {
        TestSession anonymous = TestSession.anonymous(port);

        assertThat(anonymous.send("GET", "/", null).statusCode()).isEqualTo(200);
        assertThat(anonymous.send("GET", "/runs", null).statusCode()).isEqualTo(200);
        assertThat(anonymous.send("GET", "/actuator/health", null).statusCode()).isIn(200, 503);
        assertThat(anonymous.send("GET", "/api/openapi", null).statusCode()).isEqualTo(200);
    }

    @Test
    void noUserDetailsServiceExistsSoNoPasswordIsGenerated() {
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }
}
