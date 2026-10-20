package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;

/**
 * Disabling, resetting and demoting users through HTTP: the guard rails that keep an admin, and the sessions and
 * event streams that end at once.
 */
class UserLifecycleIntegrationTest extends UsersApiTestBase {

    // ---- disable and enable ----

    @Test
    void disablingAUserEndsTheirSessionAndStreamAndBlocksTheirLogin() throws Exception {
        TestSession admin = admin();
        Account account = newAccount(RoleId.ANALYST);
        TestSession target = login(account);
        String run = body(target.send("POST", "/api/analyses", runRequest()))
                .get("id")
                .asString();
        InputStream stream = openStream(target, run);

        HttpResponse<String> response = admin.send("PATCH", "/api/users/" + account.id(), "{\"enabled\":false}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body(response).get("enabled").asBoolean()).isFalse();
        assertThat(streamLines(stream)).noneMatch(line -> line.startsWith("event:end"));
        HttpResponse<String> next = target.send("GET", "/api/analyses", null);
        assertThat(next.statusCode()).isEqualTo(401);
        assertThat(errorCode(next)).isEqualTo("unauthorized");
        HttpResponse<String> relogin = TestSession.anonymous(port()).login(account.username(), TestSession.PASSWORD);
        assertThat(relogin.statusCode()).isEqualTo(401);
        assertThat(errorCode(relogin)).isEqualTo("bad_credentials");

        HttpResponse<String> enable = admin.send("PATCH", "/api/users/" + account.id(), "{\"enabled\":true}");

        assertThat(enable.statusCode()).isEqualTo(200);
        assertThat(body(enable).get("enabled").asBoolean()).isTrue();
        assertThat(login(account).send("GET", "/api/auth/me", null).statusCode())
                .isEqualTo(200);
    }

    @Test
    void theUiOfADisabledUsersSessionStillLoadsWithoutTheSession() throws Exception {
        Account account = newAccount(RoleId.VIEWER);
        TestSession target = login(account);

        admin().send("PATCH", "/api/users/" + account.id(), "{\"enabled\":false}");

        HttpResponse<String> script = target.send("GET", "/assets/RunsPage.js?v=1", null);
        assertThat(script.statusCode()).isEqualTo(302);
        assertThat(script.headers().firstValue("Location"))
                .hasValueSatisfying(url -> assertThat(url).endsWith("/assets/RunsPage.js?v=1"));
        HttpResponse<String> api = target.send("GET", "/api/analyses", null);
        assertThat(api.statusCode()).isEqualTo(401);
        assertThat(errorCode(api)).isEqualTo("unauthorized");
    }

    // ---- guard rails ----

    @Test
    void anAdminCannotDisableThemselvesOrRemoveTheirOwnAdminRole() throws Exception {
        Account account = newAccount(RoleId.ADMIN);
        TestSession admin = login(account);

        HttpResponse<String> disable = admin.send("PATCH", "/api/users/" + account.id(), "{\"enabled\":false}");
        assertThat(disable.statusCode()).isEqualTo(409);
        assertThat(errorCode(disable)).isEqualTo("cannot_disable_self");

        HttpResponse<String> demote =
                admin.send("PATCH", "/api/users/" + account.id(), "{\"roleIds\":" + ids(RoleId.ANALYST) + "}");
        assertThat(demote.statusCode()).isEqualTo(409);
        assertThat(errorCode(demote)).isEqualTo("cannot_remove_own_admin");

        assertThat(admin.send("GET", "/api/users", null).statusCode()).isEqualTo(200);
    }

    @Test
    void theOnlyEnabledAdminCannotBeDisabledOrDemotedUntilAnotherExists() throws Exception {
        Account onlyAdmin = newAccount(RoleId.ADMIN);
        disableOtherAdminsThan(onlyAdmin);
        TestSession manager = userWithPermissions(Permission.USER_READ, Permission.USER_MANAGE);
        String path = "/api/users/" + onlyAdmin.id();

        HttpResponse<String> disable = manager.send("PATCH", path, "{\"enabled\":false}");
        assertThat(disable.statusCode()).isEqualTo(409);
        assertThat(errorCode(disable)).isEqualTo("last_admin");
        HttpResponse<String> demote = manager.send("PATCH", path, "{\"roleIds\":" + ids(RoleId.VIEWER) + "}");
        assertThat(demote.statusCode()).isEqualTo(409);
        assertThat(errorCode(demote)).isEqualTo("last_admin");
        assertThat(roleNames(getUser(manager, onlyAdmin.id()))).containsExactly("ADMIN");

        newAccount(RoleId.ADMIN);

        assertThat(manager.send("PATCH", path, "{\"roleIds\":" + ids(RoleId.VIEWER) + "}")
                        .statusCode())
                .isEqualTo(200);
    }

    @Test
    void aSecondEnabledAdminLetsTheFirstBeDisabled() throws Exception {
        Account first = newAccount(RoleId.ADMIN);
        disableOtherAdminsThan(first);
        newAccount(RoleId.ADMIN);
        TestSession manager = userWithPermissions(Permission.USER_READ, Permission.USER_MANAGE);

        HttpResponse<String> response = manager.send("PATCH", "/api/users/" + first.id(), "{\"enabled\":false}");

        assertThat(response.statusCode()).isEqualTo(200);
    }

    private void disableOtherAdminsThan(Account keep) {
        for (User user : users().findAll()) {
            if (user.enabled()
                    && user.roleIds().contains(RoleId.ADMIN)
                    && !user.id().value().equals(keep.id())) {
                users().save(user.withEnabled(false));
            }
        }
    }

    // ---- role changes ----

    @Test
    void aRoleChangeEndsTheSessionsOfTheTargetOnly() throws Exception {
        TestSession admin = admin();
        Account account = newAccount(RoleId.ANALYST);
        TestSession first = login(account);
        TestSession second = login(account);

        assertThat(admin.send("PATCH", "/api/users/" + account.id(), "{\"roleIds\":" + ids(RoleId.VIEWER) + "}")
                        .statusCode())
                .isEqualTo(200);

        assertThat(first.send("GET", "/api/analyses", null).statusCode()).isEqualTo(401);
        assertThat(second.send("GET", "/api/analyses", null).statusCode()).isEqualTo(401);
        assertThat(admin.send("GET", "/api/users", null).statusCode()).isEqualTo(200);
        TestSession again = login(account);
        assertThat(texts(body(again.send("GET", "/api/auth/me", null)).get("roles")))
                .containsExactly("VIEWER");
    }

    @Test
    void aPatchThatKeepsTheRolesKeepsTheSessions() throws Exception {
        TestSession admin = admin();
        Account account = newAccount(RoleId.ANALYST);
        TestSession target = login(account);

        assertThat(admin.send("PATCH", "/api/users/" + account.id(), "{\"roleIds\":" + ids(RoleId.ANALYST) + "}")
                        .statusCode())
                .isEqualTo(200);

        assertThat(target.send("GET", "/api/analyses", null).statusCode()).isEqualTo(200);
    }

    @Test
    void anAdminWhoChangesTheirOwnRolesKeepsTheirSession() throws Exception {
        Account account = newAccount(RoleId.ADMIN);
        TestSession admin = login(account);
        TestSession otherSession = login(account);

        HttpResponse<String> response = admin.send(
                "PATCH", "/api/users/" + account.id(), "{\"roleIds\":" + ids(RoleId.ADMIN, RoleId.ANALYST) + "}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(roleNames(body(response))).containsExactly("ADMIN", "ANALYST");
        assertThat(admin.send("GET", "/api/users", null).statusCode()).isEqualTo(200);
        assertThat(otherSession.send("GET", "/api/users", null).statusCode()).isEqualTo(401);
    }

    // ---- password reset ----

    @Test
    void aResetWithAGeneratedPasswordEndsSessionsUnlocksAndFlagsTheUser() throws Exception {
        TestSession admin = admin();
        Account account = newAccount(RoleId.ANALYST);
        TestSession target = login(account);
        lock(account, Instant.now().plus(Duration.ofHours(1)));

        HttpResponse<String> response = admin.send("POST", "/api/users/" + account.id() + "/password-reset", "{}");

        assertThat(response.statusCode()).isEqualTo(200);
        String oneTime = body(response).get("oneTimePassword").asString();
        assertThat(oneTime).hasSizeGreaterThanOrEqualTo(24);
        assertThat(target.send("GET", "/api/analyses", null).statusCode()).isEqualTo(401);
        assertThat(TestSession.anonymous(port())
                        .login(account.username(), TestSession.PASSWORD)
                        .statusCode())
                .isEqualTo(401);
        TestSession fresh = login(account.username(), oneTime);
        assertThat(body(fresh.send("GET", "/api/auth/me", null))
                        .get("mustChangePassword")
                        .asBoolean())
                .isTrue();
        assertThat(fresh.send("GET", "/api/analyses", null).statusCode()).isEqualTo(403);
        JsonNode shown = getUser(admin, account.id());
        assertThat(shown.get("locked").asBoolean()).isFalse();
        assertThat(shown.get("mustChangePassword").asBoolean()).isTrue();
    }

    @Test
    void aResetWithAnEnteredPasswordReturnsNoPassword() throws Exception {
        TestSession admin = admin();
        Account account = newAccount(RoleId.ANALYST);

        HttpResponse<String> response = admin.send(
                "POST",
                "/api/users/" + account.id() + "/password-reset",
                "{\"password\":\"" + ENTERED_PASSWORD + "\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(textOrNull(body(response), "oneTimePassword")).isNull();
        TestSession session = login(account.username(), ENTERED_PASSWORD);
        assertThat(session.send("GET", "/api/analyses", null).statusCode()).isEqualTo(403);
    }

    @Test
    void aResetWithAWeakPasswordIs400AndChangesNothing() throws Exception {
        TestSession admin = admin();
        Account account = newAccount(RoleId.ANALYST);
        TestSession target = login(account);

        HttpResponse<String> response =
                admin.send("POST", "/api/users/" + account.id() + "/password-reset", "{\"password\":\"short\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(errorCode(response)).isEqualTo("weak_password");
        assertThat(target.send("GET", "/api/analyses", null).statusCode()).isEqualTo(200);
        assertThat(login(account).send("GET", "/api/auth/me", null).statusCode())
                .isEqualTo(200);
    }

    @Test
    void anAdminWhoResetsTheirOwnPasswordIsLoggedOut() throws Exception {
        Account account = newAccount(RoleId.ADMIN);
        TestSession admin = login(account);

        HttpResponse<String> response = admin.send("POST", "/api/users/" + account.id() + "/password-reset", "{}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(admin.send("GET", "/api/users", null).statusCode()).isEqualTo(401);
        login(account.username(), body(response).get("oneTimePassword").asString());
    }

    // ---- the event stream ----

    private static String runRequest() {
        return """
                {"ticker":"SLOW","tradeDate":"2026-09-25","analysts":["MARKET"]}""";
    }

    private static InputStream openStream(TestSession session, String run) throws Exception {
        HttpResponse<InputStream> response = session.send(
                session.builder("/api/analyses/" + run + "/events")
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "text/event-stream"),
                HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    // Reads the stream to its end: a closed stream ends early, one that is left open ends with an "end" event.
    private static List<String> streamLines(InputStream body) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
