package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;

/** The roles API over HTTP: its permissions, the rules of built-in and assigned roles, and the effect on sessions. */
class RolesApiIntegrationTest extends UsersApiTestBase {

    @Test
    void aUserWithUserReadOnlyReadsRolesButChangesNone() throws Exception {
        TestSession reader = userWithPermissions(Permission.USER_READ);
        String id = createRole(admin(), newRoleName(), "[]").get("id").asString();

        assertThat(reader.send("GET", "/api/roles", null).statusCode()).isEqualTo(200);
        assertThat(reader.send("GET", "/api/roles/" + id, null).statusCode()).isEqualTo(200);

        assertThat(reader.send("POST", "/api/roles", "{\"name\":\"nope\",\"permissions\":[]}")
                        .statusCode())
                .isEqualTo(403);
        assertThat(reader.send("PATCH", "/api/roles/" + id, "{\"description\":\"x\"}")
                        .statusCode())
                .isEqualTo(403);
        assertThat(reader.send("DELETE", "/api/roles/" + id, null).statusCode()).isEqualTo(403);
    }

    @Test
    void aUserWithRoleManageOnlyDoesEverything() throws Exception {
        TestSession manager = userWithPermissions(Permission.ROLE_MANAGE);
        String name = newRoleName();

        HttpResponse<String> created = manager.send("POST", "/api/roles", createRoleBody(name, "[]"));
        assertThat(created.statusCode()).isEqualTo(201);
        String id = body(created).get("id").asString();
        assertThat(created.headers().firstValue("Location")).hasValue("/api/roles/" + id);

        assertThat(manager.send("GET", "/api/roles", null).statusCode()).isEqualTo(200);
        assertThat(manager.send("GET", "/api/roles/" + id, null).statusCode()).isEqualTo(200);
        assertThat(manager.send("PATCH", "/api/roles/" + id, "{\"description\":\"x\"}")
                        .statusCode())
                .isEqualTo(200);
        assertThat(manager.send("DELETE", "/api/roles/" + id, null).statusCode())
                .isEqualTo(204);
    }

    @Test
    void aCustomRoleIsCreatedReadChangedAndDeleted() throws Exception {
        TestSession admin = admin();
        String name = newRoleName();

        HttpResponse<String> created = admin.send(
                "POST",
                "/api/roles",
                "{\"name\":\"  %s \",\"description\":\" d \",\"permissions\":[\"preset:read\"]}".formatted(name));
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode role = body(created);
        String id = role.get("id").asString();
        assertThat(role.get("name").asString()).isEqualTo(name);
        assertThat(role.get("description").asString()).isEqualTo("d");
        assertThat(role.get("builtIn").asBoolean()).isFalse();
        assertThat(role.get("userCount").asLong()).isZero();
        assertThat(texts(role.get("permissions"))).containsExactly("preset:read");

        JsonNode read = body(admin.send("GET", "/api/roles/" + id, null));
        assertThat(read.get("name").asString()).isEqualTo(name);

        String renamed = newRoleName();
        JsonNode renamedRole = body(admin.send("PATCH", "/api/roles/" + id, "{\"name\":\"%s\"}".formatted(renamed)));
        assertThat(renamedRole.get("name").asString()).isEqualTo(renamed);
        assertThat(texts(renamedRole.get("permissions"))).containsExactly("preset:read");

        JsonNode changed = body(
                admin.send("PATCH", "/api/roles/" + id, "{\"permissions\":[\"analysis:read\",\"preset:manage\"]}"));
        assertThat(texts(changed.get("permissions"))).containsExactly("analysis:read", "preset:manage");
        assertThat(changed.get("name").asString()).isEqualTo(renamed);

        assertThat(body(admin.send("GET", "/api/roles", null))
                        .valueStream()
                        .map(r -> r.get("id").asString()))
                .contains(id);

        assertThat(admin.send("DELETE", "/api/roles/" + id, null).statusCode()).isEqualTo(204);
        assertThat(admin.send("GET", "/api/roles/" + id, null).statusCode()).isEqualTo(404);
    }

    @Test
    void invalidRequestsAreAnsweredWithTheirCodes() throws Exception {
        TestSession admin = admin();
        String name = newRoleName();
        String id = createRole(admin, name, "[]").get("id").asString();

        HttpResponse<String> taken = admin.send("POST", "/api/roles", createRoleBody(name, "[]"));
        assertThat(taken.statusCode()).isEqualTo(409);
        assertThat(errorCode(taken)).isEqualTo("role_name_taken");
        HttpResponse<String> takenOtherCase =
                admin.send("POST", "/api/roles", createRoleBody(name.toUpperCase(), "[]"));
        assertThat(takenOtherCase.statusCode()).isEqualTo(409);
        assertThat(errorCode(takenOtherCase)).isEqualTo("role_name_taken");
        HttpResponse<String> builtInName = admin.send("POST", "/api/roles", createRoleBody("admin", "[]"));
        assertThat(errorCode(builtInName)).isEqualTo("role_name_taken");

        String other = createRole(admin, newRoleName(), "[]").get("id").asString();
        HttpResponse<String> renameTaken =
                admin.send("PATCH", "/api/roles/" + other, "{\"name\":\"%s\"}".formatted(name));
        assertThat(renameTaken.statusCode()).isEqualTo(409);
        assertThat(errorCode(renameTaken)).isEqualTo("role_name_taken");

        HttpResponse<String> invalidName = admin.send("POST", "/api/roles", createRoleBody("x", "[]"));
        assertThat(invalidName.statusCode()).isEqualTo(400);
        assertThat(errorCode(invalidName)).isEqualTo("invalid_role_name");
        HttpResponse<String> invalidChars = admin.send("POST", "/api/roles", createRoleBody("bad/name", "[]"));
        assertThat(errorCode(invalidChars)).isEqualTo("invalid_role_name");
        HttpResponse<String> invalidDescription = admin.send(
                "POST",
                "/api/roles",
                "{\"name\":\"%s\",\"description\":\"%s\",\"permissions\":[]}"
                        .formatted(newRoleName(), "d".repeat(201)));
        assertThat(invalidDescription.statusCode()).isEqualTo(400);
        assertThat(errorCode(invalidDescription)).isEqualTo("invalid_role_description");

        HttpResponse<String> empty = admin.send("PATCH", "/api/roles/" + id, "{}");
        assertThat(empty.statusCode()).isEqualTo(400);
        assertThat(errorCode(empty)).isEqualTo("invalid_request");
        HttpResponse<String> unknownKey =
                admin.send("POST", "/api/roles", createRoleBody(newRoleName(), "[\"no:such\"]"));
        assertThat(unknownKey.statusCode()).isEqualTo(400);
        assertThat(errorCode(unknownKey)).isEqualTo("invalid_request");
        HttpResponse<String> unknownKeyPatch =
                admin.send("PATCH", "/api/roles/" + id, "{\"permissions\":[\"no:such\"]}");
        assertThat(unknownKeyPatch.statusCode()).isEqualTo(400);
        assertThat(errorCode(unknownKeyPatch)).isEqualTo("invalid_request");

        String missing = UUID.randomUUID().toString();
        for (String[] request :
                new String[][] {{"GET", null}, {"PATCH", "{\"description\":\"x\"}"}, {"DELETE", null}}) {
            HttpResponse<String> response = admin.send(request[0], "/api/roles/" + missing, request[1]);
            assertThat(response.statusCode()).as(request[0]).isEqualTo(404);
            assertThat(errorCode(response)).isEqualTo("role_not_found");
        }
    }

    @Test
    void aStaleVersionIsAConcurrentUpdate() throws Exception {
        TestSession admin = admin();
        JsonNode role = createRole(admin, newRoleName(), "[]");
        String id = role.get("id").asString();
        long version = role.get("version").asLong();

        HttpResponse<String> first =
                admin.send("PATCH", "/api/roles/" + id, "{\"description\":\"one\",\"version\":%d}".formatted(version));
        assertThat(first.statusCode()).isEqualTo(200);

        HttpResponse<String> stale =
                admin.send("PATCH", "/api/roles/" + id, "{\"description\":\"two\",\"version\":%d}".formatted(version));
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(errorCode(stale)).isEqualTo("concurrent_update");
    }

    @Test
    void builtInRolesCannotBeRenamedOrDeletedAndAdminKeepsItsPermissions() throws Exception {
        TestSession admin = admin();

        for (RoleId builtIn : new RoleId[] {RoleId.ADMIN, RoleId.ANALYST, RoleId.VIEWER, RoleId.PUBLISHER}) {
            String path = "/api/roles/" + builtIn.value();
            HttpResponse<String> rename = admin.send("PATCH", path, "{\"name\":\"Renamed\"}");
            assertThat(rename.statusCode()).as("rename " + builtIn.value()).isEqualTo(409);
            assertThat(errorCode(rename)).isEqualTo("built_in_role");
            HttpResponse<String> delete = admin.send("DELETE", path, null);
            assertThat(delete.statusCode()).as("delete " + builtIn.value()).isEqualTo(409);
            assertThat(errorCode(delete)).isEqualTo("built_in_role");
        }

        HttpResponse<String> adminPermissions =
                admin.send("PATCH", "/api/roles/" + RoleId.ADMIN.value(), "{\"permissions\":[\"analysis:read\"]}");
        assertThat(adminPermissions.statusCode()).isEqualTo(409);
        assertThat(errorCode(adminPermissions)).isEqualTo("built_in_role");
    }

    @Test
    void thePublisherRoleIsListedAsBuiltInWithOnlyAnalysisShare() throws Exception {
        JsonNode publisher = body(admin().send("GET", "/api/roles/" + RoleId.PUBLISHER.value(), null));

        assertThat(publisher.get("builtIn").asBoolean()).isTrue();
        assertThat(publisher.get("name").asString()).isEqualTo("PUBLISHER");
        assertThat(texts(publisher.get("permissions"))).containsExactly("analysis:share");
    }

    @Test
    void aBuiltInRoleKeepsItsPermissionsAfterAnUnchangedNameAndItsPermissionsCanBeChanged() throws Exception {
        TestSession admin = admin();
        String path = "/api/roles/" + RoleId.VIEWER.value();
        JsonNode viewer = body(admin.send("GET", path, null));
        assertThat(viewer.get("builtIn").asBoolean()).isTrue();
        String original = viewer.get("name").asString();
        String originalPermissions = viewer.get("permissions").toString();

        try {
            HttpResponse<String> same = admin.send(
                    "PATCH", path, "{\"name\":\"%s\",\"permissions\":[\"analysis:read\"]}".formatted(original));
            assertThat(same.statusCode()).isEqualTo(200);
            assertThat(texts(body(same).get("permissions"))).containsExactly("analysis:read");
        } finally {
            assertThat(admin.send("PATCH", path, "{\"permissions\":%s}".formatted(originalPermissions))
                            .statusCode())
                    .isEqualTo(200);
        }
    }

    @Test
    void anAssignedRoleCannotBeDeleted() throws Exception {
        TestSession admin = admin();
        String id = createRole(admin, newRoleName(), "[]").get("id").asString();
        Account holder = newAccount(new RoleId(id));
        Account disabledHolder = newAccount(new RoleId(id));
        assertThat(admin.send("PATCH", "/api/users/" + disabledHolder.id(), "{\"enabled\":false}")
                        .statusCode())
                .isEqualTo(200);

        assertThat(body(admin.send("GET", "/api/roles/" + id, null))
                        .get("userCount")
                        .asLong())
                .isEqualTo(2);
        HttpResponse<String> refused = admin.send("DELETE", "/api/roles/" + id, null);
        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(errorCode(refused)).isEqualTo("role_in_use");
        assertThat(body(refused).get("params").get("userCount").asLong()).isEqualTo(2);

        assertThat(admin.send("PATCH", "/api/users/" + holder.id(), "{\"roleIds\":[]}")
                        .statusCode())
                .isEqualTo(200);
        assertThat(admin.send("DELETE", "/api/roles/" + id, null).statusCode()).isEqualTo(409);

        assertThat(admin.send("PATCH", "/api/users/" + disabledHolder.id(), "{\"roleIds\":[]}")
                        .statusCode())
                .isEqualTo(200);
        assertThat(admin.send("DELETE", "/api/roles/" + id, null).statusCode()).isEqualTo(204);
    }

    @Test
    void aChangedRoleAppliesToTheNextRequestWithoutANewLogin() throws Exception {
        TestSession admin = admin();
        String id = createRole(admin, newRoleName(), "[]").get("id").asString();
        TestSession user = login(newAccount(new RoleId(id)));

        assertThat(user.send("GET", "/api/presets", null).statusCode()).isEqualTo(403);

        assertThat(admin.send("PATCH", "/api/roles/" + id, "{\"permissions\":[\"preset:read\"]}")
                        .statusCode())
                .isEqualTo(200);
        assertThat(user.send("GET", "/api/presets", null).statusCode()).isEqualTo(200);
        JsonNode me = body(user.send("GET", "/api/auth/me", null));
        assertThat(texts(me.get("permissions"))).contains("preset:read");

        assertThat(admin.send("PATCH", "/api/roles/" + id, "{\"permissions\":[]}")
                        .statusCode())
                .isEqualTo(200);
        assertThat(user.send("GET", "/api/presets", null).statusCode()).isEqualTo(403);
        assertThat(texts(body(user.send("GET", "/api/auth/me", null)).get("permissions")))
                .doesNotContain("preset:read");
    }

    private static JsonNode createRole(TestSession session, String name, String permissions) throws Exception {
        HttpResponse<String> response = session.send("POST", "/api/roles", createRoleBody(name, permissions));
        assertThat(response.statusCode()).isEqualTo(201);
        return body(response);
    }

    private static String createRoleBody(String name, String permissions) {
        return "{\"name\":\"%s\",\"permissions\":%s}".formatted(name, permissions);
    }

    private static String newRoleName() {
        return "role-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
