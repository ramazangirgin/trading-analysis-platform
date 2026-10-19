package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

/**
 * The user management API through HTTP: the permissions of every endpoint, creating users, reading them and
 * changing their roles. Disabling, the guard rails and the sessions that end are in
 * {@link UserLifecycleIntegrationTest}.
 */
class UsersApiIntegrationTest extends UsersApiTestBase {

    private static final String NEW_PASSWORD = "a brand new password";

    private static List<String[]> endpoints() {
        String id = UserId.newId().value();
        return List.of(
                new String[] {"GET", "/api/users"},
                new String[] {"GET", "/api/users/" + id},
                new String[] {"POST", "/api/users"},
                new String[] {"PATCH", "/api/users/" + id},
                new String[] {"POST", "/api/users/" + id + "/password-reset"},
                new String[] {"POST", "/api/users/" + id + "/unlock"},
                new String[] {"GET", "/api/roles"});
    }

    private static HttpResponse<String> call(TestSession session, String[] endpoint) throws Exception {
        boolean hasBody = !endpoint[0].equals("GET") && !endpoint[1].endsWith("/unlock");
        return session.send(endpoint[0], endpoint[1], hasBody ? TestSession.validBody(endpoint[0], endpoint[1]) : null);
    }

    // ---- permissions ----

    @Test
    void everyEndpointAnswers401WithoutASession() throws Exception {
        TestSession anonymous = TestSession.anonymous(port());

        for (String[] endpoint : endpoints()) {
            HttpResponse<String> response = call(anonymous, endpoint);
            assertThat(response.statusCode())
                    .as(endpoint[0] + " " + endpoint[1])
                    .isEqualTo(401);
            assertThat(errorCode(response)).isEqualTo("unauthorized");
        }
    }

    @Test
    void aUserWithoutRolesGets403Everywhere() throws Exception {
        TestSession session = login(newAccount());

        for (String[] endpoint : endpoints()) {
            HttpResponse<String> response = call(session, endpoint);
            assertThat(response.statusCode())
                    .as(endpoint[0] + " " + endpoint[1])
                    .isEqualTo(403);
            assertThat(errorCode(response)).isEqualTo("forbidden");
        }
    }

    @Test
    void aUserWhoMayOnlyReadUsersCannotChangeAnything() throws Exception {
        TestSession reader = userWithPermissions(Permission.USER_READ);
        String id = newAccount().id();

        assertThat(reader.send("GET", "/api/users", null).statusCode()).isEqualTo(200);
        assertThat(reader.send("GET", "/api/users/" + id, null).statusCode()).isEqualTo(200);
        assertThat(reader.send("GET", "/api/roles", null).statusCode()).isEqualTo(200);
        for (String[] endpoint : endpoints()) {
            if (!endpoint[0].equals("GET")) {
                assertThat(call(reader, endpoint).statusCode())
                        .as(endpoint[0] + " " + endpoint[1])
                        .isEqualTo(403);
            }
        }
    }

    @Test
    void theRoleListIsOpenToRoleManagersToo() throws Exception {
        TestSession manager = userWithPermissions(Permission.ROLE_MANAGE);

        assertThat(manager.send("GET", "/api/roles", null).statusCode()).isEqualTo(200);
        assertThat(manager.send("GET", "/api/users", null).statusCode()).isEqualTo(403);
    }

    @Test
    void anAdminMayCallEveryEndpoint() throws Exception {
        TestSession admin = admin();
        String id = newAccount().id();

        assertThat(admin.send("GET", "/api/users", null).statusCode()).isEqualTo(200);
        assertThat(admin.send("GET", "/api/users/" + id, null).statusCode()).isEqualTo(200);
        assertThat(admin.send("GET", "/api/roles", null).statusCode()).isEqualTo(200);
        assertThat(admin.send("POST", "/api/users/" + id + "/unlock", null).statusCode())
                .isEqualTo(204);
        assertThat(admin.send("PATCH", "/api/users/" + id, "{\"enabled\":true}").statusCode())
                .isEqualTo(200);
        assertThat(admin.send("POST", "/api/users/" + id + "/password-reset", "{}")
                        .statusCode())
                .isEqualTo(200);
        assertThat(admin.send("POST", "/api/users", createBody(newUsername(), "[]", null))
                        .statusCode())
                .isEqualTo(201);
    }

    // ---- create ----

    @Test
    void aUserCreatedWithAnEnteredPasswordLogsInAndMustChangeIt() throws Exception {
        TestSession admin = admin();
        String username = newUsername();

        HttpResponse<String> response =
                admin.send("POST", "/api/users", createBody(username, ids(RoleId.ANALYST), ENTERED_PASSWORD));

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode created = body(response);
        assertThat(textOrNull(created, "oneTimePassword")).isNull();
        JsonNode user = created.get("user");
        assertThat(user.get("username").asString()).isEqualTo(username);
        assertThat(roleNames(user)).containsExactly("ANALYST");
        assertThat(user.get("enabled").asBoolean()).isTrue();
        assertThat(user.get("mustChangePassword").asBoolean()).isTrue();
        assertThat(user.get("locked").asBoolean()).isFalse();
        assertThat(textOrNull(user, "lastLoginAt")).isNull();
        assertThat(user.get("createdAt").asString()).isNotBlank();
        assertThat(user.get("version")).isNotNull();

        TestSession session = login(username, ENTERED_PASSWORD);
        HttpResponse<String> gated = session.send("GET", "/api/analyses", null);
        assertThat(gated.statusCode()).isEqualTo(403);
        assertThat(errorCode(gated)).isEqualTo("password_change_required");
        assertThat(session.changePassword(ENTERED_PASSWORD, NEW_PASSWORD).statusCode())
                .isEqualTo(200);
        assertThat(session.send("GET", "/api/analyses", null).statusCode()).isEqualTo(200);
        assertThat(getUser(admin, user.get("id").asString())
                        .get("mustChangePassword")
                        .asBoolean())
                .isFalse();
    }

    @Test
    void aUserCreatedWithoutAPasswordGetsAGeneratedOneThatLogsIn() throws Exception {
        TestSession admin = admin();

        for (String blank : new String[] {null, "", "  "}) {
            String name = newUsername();
            JsonNode created = body(admin.send("POST", "/api/users", createBody(name, ids(RoleId.VIEWER), blank)));

            String oneTime = created.get("oneTimePassword").asString();
            assertThat(oneTime).hasSizeGreaterThanOrEqualTo(24);
            assertThat(created.get("user").get("mustChangePassword").asBoolean())
                    .isTrue();
            TestSession session = login(name, oneTime);
            assertThat(session.send("GET", "/api/analyses", null).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void createRefusesWhatIsNotAcceptable() throws Exception {
        TestSession admin = admin();

        HttpResponse<String> weak =
                admin.send("POST", "/api/users", createBody(newUsername(), ids(RoleId.VIEWER), "short"));
        assertThat(weak.statusCode()).isEqualTo(400);
        assertThat(errorCode(weak)).isEqualTo("weak_password");
        assertThat(texts(body(weak).get("params").get("reasons"))).contains("too_short");
        assertThat(weak.body()).doesNotContain("\"short\"");

        HttpResponse<String> invalid = admin.send("POST", "/api/users", createBody("a b", ids(RoleId.VIEWER), null));
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(errorCode(invalid)).isEqualTo("invalid_username");

        HttpResponse<String> unknownRole =
                admin.send("POST", "/api/users", createBody(newUsername(), "[\"nope\"]", null));
        assertThat(unknownRole.statusCode()).isEqualTo(400);
        assertThat(errorCode(unknownRole)).isEqualTo("unknown_role");
        assertThat(body(unknownRole).get("params").get("roleId").asString()).isEqualTo("nope");

        HttpResponse<String> missing = admin.send("POST", "/api/users", "{\"username\":\"someone-else\"}");
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(errorCode(missing)).isEqualTo("invalid_request");
    }

    @Test
    void aTakenUsernameIs409EvenInAnotherCase() throws Exception {
        TestSession admin = admin();
        String username = newAccount().username();

        for (String name : new String[] {username, username.toUpperCase(Locale.ROOT)}) {
            HttpResponse<String> response = admin.send("POST", "/api/users", createBody(name, "[]", null));
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(errorCode(response)).isEqualTo("username_taken");
        }
    }

    // ---- list and get ----

    @Test
    void listAndGetShowRolesStatusLockAndLastLogin() throws Exception {
        TestSession admin = admin();
        Account account = newAccount(RoleId.VIEWER, RoleId.ANALYST);
        String id = account.id();

        JsonNode before = getUser(admin, id);
        assertThat(before.get("username").asString()).isEqualTo(account.username());
        assertThat(roleNames(before)).containsExactly("ANALYST", "VIEWER");
        assertThat(before.get("enabled").asBoolean()).isTrue();
        assertThat(before.get("mustChangePassword").asBoolean()).isFalse();
        assertThat(before.get("locked").asBoolean()).isFalse();
        assertThat(textOrNull(before, "lockedUntil")).isNull();
        assertThat(textOrNull(before, "lastLoginAt")).isNull();

        login(account);

        assertThat(textOrNull(getUser(admin, id), "lastLoginAt")).isNotNull();
        JsonNode listed = body(admin.send("GET", "/api/users", null));
        assertThat(listed.valueStream().map(u -> u.get("id").asString())).contains(id);
        List<String> names =
                listed.valueStream().map(u -> u.get("username").asString()).toList();
        assertThat(names).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    void aLockedUserIsShownAsLockedUntilTheLockExpires() throws Exception {
        TestSession admin = admin();
        Account locked = newAccount();
        Account expired = newAccount();
        lock(locked, Instant.now().plus(Duration.ofHours(1)));
        lock(expired, Instant.now().minus(Duration.ofHours(1)));

        JsonNode shown = getUser(admin, locked.id());
        assertThat(shown.get("locked").asBoolean()).isTrue();
        assertThat(textOrNull(shown, "lockedUntil")).isNotNull();
        assertThat(getUser(admin, expired.id()).get("locked").asBoolean()).isFalse();
    }

    @Test
    void anUnknownUserIs404() throws Exception {
        TestSession admin = admin();
        String id = UserId.newId().value();

        for (String[] endpoint : new String[][] {
            {"GET", "/api/users/" + id},
            {"PATCH", "/api/users/" + id},
            {"POST", "/api/users/" + id + "/password-reset"}
        }) {
            HttpResponse<String> response = admin.send(
                    endpoint[0],
                    endpoint[1],
                    endpoint[0].equals("GET") ? null : TestSession.validBody(endpoint[0], endpoint[1]));
            assertThat(response.statusCode())
                    .as(endpoint[0] + " " + endpoint[1])
                    .isEqualTo(404);
            assertThat(errorCode(response)).isEqualTo("user_not_found");
        }
    }

    @Test
    void theRoleListHasTheBuiltInRolesWithTheirPermissions() throws Exception {
        JsonNode list = body(admin().send("GET", "/api/roles", null));

        assertThat(list.valueStream().map(r -> r.get("name").asString()))
                .containsSubsequence("ADMIN", "ANALYST", "VIEWER");
        JsonNode adminRole = list.valueStream()
                .filter(r -> r.get("id").asString().equals("ADMIN"))
                .findFirst()
                .orElseThrow();
        assertThat(adminRole.get("builtIn").asBoolean()).isTrue();
        assertThat(texts(adminRole.get("permissions"))).contains("user:manage", "user:read", "role:manage");
        JsonNode viewer = list.valueStream()
                .filter(r -> r.get("id").asString().equals("VIEWER"))
                .findFirst()
                .orElseThrow();
        assertThat(texts(viewer.get("permissions"))).isNotEmpty().doesNotContain("user:manage");
    }

    // ---- update ----

    @Test
    void anAdminChangesTheRolesOfAUser() throws Exception {
        TestSession admin = admin();
        String id = newAccount(RoleId.VIEWER).id();

        HttpResponse<String> response =
                admin.send("PATCH", "/api/users/" + id, "{\"roleIds\":" + ids(RoleId.ANALYST) + "}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(roleNames(body(response))).containsExactly("ANALYST");
        assertThat(roleNames(getUser(admin, id))).containsExactly("ANALYST");
    }

    @Test
    void aPatchWithNeitherRolesNorEnabledIs400() throws Exception {
        TestSession admin = admin();
        String id = newAccount().id();

        HttpResponse<String> response = admin.send("PATCH", "/api/users/" + id, "{}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(errorCode(response)).isEqualTo("invalid_request");
    }

    @Test
    void aPatchWithAnUnknownRoleIs400() throws Exception {
        TestSession admin = admin();
        String id = newAccount().id();

        HttpResponse<String> response = admin.send("PATCH", "/api/users/" + id, "{\"roleIds\":[\"nope\"]}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(errorCode(response)).isEqualTo("unknown_role");
    }

    @Test
    void aStaleVersionIs409() throws Exception {
        TestSession admin = admin();
        String id = newAccount(RoleId.VIEWER).id();
        long stale = getUser(admin, id).get("version").asLong();
        assertThat(admin.send("PATCH", "/api/users/" + id, "{\"roleIds\":" + ids(RoleId.ANALYST) + "}")
                        .statusCode())
                .isEqualTo(200);

        HttpResponse<String> response = admin.send(
                "PATCH", "/api/users/" + id, "{\"roleIds\":" + ids(RoleId.VIEWER) + ",\"version\":" + stale + "}");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(errorCode(response)).isEqualTo("concurrent_update");
        long current = getUser(admin, id).get("version").asLong();
        assertThat(admin.send("PATCH", "/api/users/" + id, "{\"enabled\":true,\"version\":" + current + "}")
                        .statusCode())
                .isEqualTo(200);
    }
}
