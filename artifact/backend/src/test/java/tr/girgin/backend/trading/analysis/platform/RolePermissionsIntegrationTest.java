package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
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
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/** What the built-in roles may do over HTTP: the permission rules of the security chain, role by role. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class RolePermissionsIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private PasswordHasherPort hasher;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestPlatformHome.register(registry, HOME, HOME.resolve("runner.sh"));
    }

    @Test
    void aViewerIsRefusedEverythingButReadingAnalysesAndPresets() throws Exception {
        TestSession viewer = TestSession.loggedIn(port, users, hasher, RoleId.VIEWER);
        String id = UUID.randomUUID().toString();

        List<String[]> refused = List.of(
                new String[] {"POST", "/api/analyses"},
                new String[] {"POST", "/api/analyses/" + id + "/stop"},
                new String[] {"POST", "/api/analyses/" + id + "/rerun"},
                new String[] {"POST", "/api/reports/rescan"},
                new String[] {"PUT", "/api/secrets/OPENAI_API_KEY"},
                new String[] {"DELETE", "/api/secrets/OPENAI_API_KEY"},
                new String[] {"POST", "/api/presets"},
                new String[] {"PUT", "/api/presets/" + id},
                new String[] {"DELETE", "/api/presets/" + id},
                new String[] {"GET", "/api/secrets"},
                new String[] {"GET", "/api/catalog"},
                new String[] {"GET", "/api/health"},
                new String[] {"GET", "/api/settings/analysis-defaults"},
                new String[] {"PUT", "/api/settings/analysis-defaults"},
                new String[] {"POST", "/api/users/" + id + "/unlock"});
        for (String[] request : refused) {
            HttpResponse<String> response =
                    viewer.send(request[0], request[1], TestSession.validBody(request[0], request[1]));
            assertThat(response.statusCode()).as(request[0] + " " + request[1]).isEqualTo(403);
            assertThat(JSON.readTree(response.body()).get("errorCode").asString())
                    .isEqualTo("forbidden");
        }

        assertThat(viewer.send("GET", "/api/analyses", null).statusCode()).isEqualTo(200);
        assertThat(viewer.send("GET", "/api/presets", null).statusCode()).isEqualTo(200);
    }

    @Test
    void anAnalystRunsAnalysesButWritesNoKeysAndUnlocksNobody() throws Exception {
        TestSession analyst = TestSession.loggedIn(port, users, hasher, RoleId.ANALYST);
        String id = UUID.randomUUID().toString();

        for (String[] request : List.of(
                new String[] {"PUT", "/api/secrets/OPENAI_API_KEY"},
                new String[] {"DELETE", "/api/secrets/OPENAI_API_KEY"},
                new String[] {"PUT", "/api/settings/analysis-defaults"},
                new String[] {"POST", "/api/users/" + id + "/unlock"})) {
            assertThat(analyst.send(request[0], request[1], TestSession.validBody(request[0], request[1]))
                            .statusCode())
                    .as(request[0] + " " + request[1])
                    .isEqualTo(403);
        }

        for (String[] request : List.of(
                new String[] {"POST", "/api/analyses"},
                new String[] {"POST", "/api/analyses/" + id + "/stop"},
                new String[] {"POST", "/api/analyses/" + id + "/rerun"},
                new String[] {"POST", "/api/reports/rescan"},
                new String[] {"POST", "/api/presets"},
                new String[] {"GET", "/api/secrets"},
                new String[] {"GET", "/api/catalog"},
                new String[] {"GET", "/api/settings/analysis-defaults"},
                new String[] {"GET", "/api/health"})) {
            assertThat(analyst.send(request[0], request[1], "{}").statusCode())
                    .as(request[0] + " " + request[1])
                    .isNotEqualTo(403);
        }
    }

    @Test
    void meReturnsTheRoleAndPermissionKeysOfEachBuiltInRole() throws Exception {
        assertThat(me(RoleId.VIEWER, "VIEWER")).containsExactlyInAnyOrder("analysis:read", "preset:read");
        assertThat(me(RoleId.ANALYST, "ANALYST"))
                .containsExactlyInAnyOrder(
                        "analysis:run",
                        "analysis:read",
                        "analysis:delete",
                        "preset:read",
                        "preset:manage",
                        "settings:read");
        assertThat(me(RoleId.ADMIN, "ADMIN"))
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(Permission.values()).map(Permission::key).toList());
    }

    private List<String> me(RoleId role, String roleName) throws Exception {
        TestSession session = TestSession.loggedIn(port, users, hasher, role);
        JsonNode me = JSON.readTree(session.send("GET", "/api/auth/me", null).body());
        assertThat(me.get("roles")).extracting(JsonNode::asString).containsExactly(roleName);
        return me.get("permissions").valueStream().map(JsonNode::asString).toList();
    }
}
