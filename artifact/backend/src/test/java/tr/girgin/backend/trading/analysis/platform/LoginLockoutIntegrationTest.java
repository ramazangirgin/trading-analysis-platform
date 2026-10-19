package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.MutableTestClock;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/**
 * The lockout and the IP limit through HTTP, with 3 wrong passwords locking an account for 15 minutes and 10
 * failed logins per address in 5 minutes. A clock that the test moves stands for the passing time.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class LoginLockoutIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_FAILED = 3;
    private static final int IP_MAX_FAILED = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private RoleRepositoryPort roles;

    @Autowired
    private PasswordHasherPort hasher;

    @Autowired
    private MutableTestClock clock;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestPlatformHome.register(registry, HOME, HOME.resolve("runner.sh"));
        registry.add("platform.login.max-failed-attempts", () -> MAX_FAILED);
        registry.add("platform.login.lockout-duration", () -> "15m");
        registry.add("platform.login.ip-max-failed-attempts", () -> IP_MAX_FAILED);
        registry.add("platform.login.ip-window", () -> "5m");
    }

    @BeforeEach
    void startWithAnEmptyIpWindow() {
        clock.advance(Duration.ofHours(1));
    }

    private static String errorCode(HttpResponse<String> response) {
        return JSON.readTree(response.body()).get("errorCode").asString();
    }

    private String createAdmin() {
        String username = "admin-" + UUID.randomUUID();
        users.save(new User(
                UserId.newId(),
                new Username(username),
                hasher.hash(TestSession.PASSWORD),
                true,
                false,
                0,
                Optional.empty(),
                Optional.empty(),
                null,
                null,
                Set.of(roles.findByName("ADMIN").orElseThrow().id()),
                null));
        return username;
    }

    private void failLoginsOf(String username, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            assertThat(TestSession.anonymous(port)
                            .login(username, "not the password")
                            .statusCode())
                    .isEqualTo(401);
        }
    }

    private HttpResponse<String> loginWithTheRightPassword(String username) throws Exception {
        return TestSession.anonymous(port).login(username, TestSession.PASSWORD);
    }

    @Test
    void aLockedAccountFailsLikeAWrongPasswordEvenWithTheRightOne() throws Exception {
        String username = TestSession.createUser(users, hasher, true);
        HttpResponse<String> wrong = TestSession.anonymous(port).login(username, "not the password");
        failLoginsOf(username, MAX_FAILED - 1);

        HttpResponse<String> locked = loginWithTheRightPassword(username);

        assertThat(locked.statusCode()).isEqualTo(401);
        assertThat(errorCode(locked)).isEqualTo("bad_credentials");
        assertThat(locked.body()).isEqualTo(wrong.body());
        assertThat(users.findByUsername(new Username(username)).orElseThrow().lockedUntil())
                .isPresent();
    }

    @Test
    void theRightPasswordWorksAgainWhenTheLockExpires() throws Exception {
        String username = TestSession.createUser(users, hasher, true);
        failLoginsOf(username, MAX_FAILED);
        assertThat(loginWithTheRightPassword(username).statusCode()).isEqualTo(401);

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        assertThat(loginWithTheRightPassword(username).statusCode()).isEqualTo(200);
        User user = users.findByUsername(new Username(username)).orElseThrow();
        assertThat(user.failedLoginCount()).isZero();
        assertThat(user.lockedUntil()).isEmpty();
    }

    @Test
    void aSuccessResetsTheCount() throws Exception {
        String username = TestSession.createUser(users, hasher, true);
        failLoginsOf(username, MAX_FAILED - 1);
        assertThat(loginWithTheRightPassword(username).statusCode()).isEqualTo(200);

        failLoginsOf(username, MAX_FAILED - 1);

        assertThat(loginWithTheRightPassword(username).statusCode()).isEqualTo(200);
    }

    @Test
    void anAdminUnlocksAnAccountEarly() throws Exception {
        String username = TestSession.createUser(users, hasher, true);
        failLoginsOf(username, MAX_FAILED);
        assertThat(loginWithTheRightPassword(username).statusCode()).isEqualTo(401);
        TestSession admin = TestSession.anonymous(port);
        assertThat(admin.login(createAdmin(), TestSession.PASSWORD).statusCode())
                .isEqualTo(200);
        String id =
                users.findByUsername(new Username(username)).orElseThrow().id().value();

        assertThat(admin.send("POST", "/api/users/" + id + "/unlock", null).statusCode())
                .isEqualTo(204);

        assertThat(loginWithTheRightPassword(username).statusCode()).isEqualTo(200);
        // Unlocking an account that is not locked succeeds as well.
        assertThat(admin.send("POST", "/api/users/" + id + "/unlock", null).statusCode())
                .isEqualTo(204);
    }

    @Test
    void unlockNeedsASessionAndTheUserManagePermission() throws Exception {
        String id = UserId.newId().value();

        HttpResponse<String> anonymous = TestSession.anonymous(port).send("POST", "/api/users/" + id + "/unlock", null);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(errorCode(anonymous)).isEqualTo("unauthorized");

        HttpResponse<String> forbidden =
                TestSession.loggedIn(port, users, hasher).send("POST", "/api/users/" + id + "/unlock", null);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(errorCode(forbidden)).isEqualTo("forbidden");
    }

    @Test
    void unlockOfAnUnknownIdIs404() throws Exception {
        TestSession admin = TestSession.anonymous(port);
        assertThat(admin.login(createAdmin(), TestSession.PASSWORD).statusCode())
                .isEqualTo(200);

        HttpResponse<String> response =
                admin.send("POST", "/api/users/" + UserId.newId().value() + "/unlock", null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(errorCode(response)).isEqualTo("user_not_found");
    }

    @Test
    void anAddressWithTooManyFailedLoginsGets429EvenForAnAccountThatIsNotLocked() throws Exception {
        String username = TestSession.createUser(users, hasher, true);
        for (int i = 0; i < IP_MAX_FAILED; i++) {
            assertThat(TestSession.anonymous(port)
                            .login("nobody-" + i, "not the password")
                            .statusCode())
                    .isEqualTo(401);
        }

        HttpResponse<String> blocked = loginWithTheRightPassword(username);

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(errorCode(blocked)).isEqualTo("too_many_login_attempts");
        assertThat(Long.parseLong(blocked.headers().firstValue("Retry-After").orElseThrow()))
                .isBetween(1L, 300L);
        assertThat(users.findByUsername(new Username(username)).orElseThrow().failedLoginCount())
                .isZero();

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertThat(loginWithTheRightPassword(username).statusCode()).isEqualTo(200);
    }

    @Test
    void behindTheProxyTheLimitCountsTheForwardedClientAndNotTheProxy() throws Exception {
        String username = TestSession.createUser(users, hasher, true);
        for (int i = 0; i < IP_MAX_FAILED; i++) {
            assertThat(TestSession.anonymous(port)
                            .withHeader("X-Forwarded-For", "203.0.113.1")
                            .login("nobody-" + i, "not the password")
                            .statusCode())
                    .isEqualTo(401);
        }

        HttpResponse<String> blocked = TestSession.anonymous(port)
                .withHeader("X-Forwarded-For", "203.0.113.1")
                .login(username, TestSession.PASSWORD);
        HttpResponse<String> otherClient = TestSession.anonymous(port)
                .withHeader("X-Forwarded-For", "203.0.113.2")
                .login(username, TestSession.PASSWORD);

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(errorCode(blocked)).isEqualTo("too_many_login_attempts");
        assertThat(otherClient.statusCode()).isEqualTo(200);
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        @Primary
        MutableTestClock testClock() {
            return new MutableTestClock();
        }
    }
}
