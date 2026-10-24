package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
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
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserDirectoryUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence.PresetRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;
import tr.girgin.backend.trading.analysis.platform.orchestration.ownership.inbound.AssignPresetOwnersUseCase;

/** Who sees which preset: a user only their own, an admin everyone's, and the unowned go to the first admin. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class PresetOwnershipIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NOT_FOUND = "preset_not_found";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private PasswordHasherPort hasher;

    @Autowired
    private PresetRepositoryPort presets;

    @Autowired
    private UserDirectoryUseCase userDirectory;

    @Autowired
    private AssignPresetOwnersUseCase assignOwners;

    private String analystAName;
    private String analystBName;
    private TestSession analystA;
    private TestSession analystB;
    private TestSession admin;
    private TestSession viewer;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestPlatformHome.register(registry, HOME, HOME.resolve("runner.sh"));
    }

    @BeforeEach
    void logIn() throws Exception {
        analystAName = TestSession.createUser(users, hasher, true, RoleId.ANALYST);
        analystBName = TestSession.createUser(users, hasher, true, RoleId.ANALYST);
        String adminName = TestSession.createUser(users, hasher, true, RoleId.ADMIN);
        String viewerName = TestSession.createUser(users, hasher, true, RoleId.VIEWER);
        analystA = loggedInAs(analystAName);
        analystB = loggedInAs(analystBName);
        admin = loggedInAs(adminName);
        viewer = loggedInAs(viewerName);
    }

    @Test
    void aUserDoesNotSeeOrChangeAnotherUsersPreset() throws Exception {
        String theirs = create(analystB, "Theirs");
        String own = create(analystA, "Own");

        assertThat(ids(analystA, "/api/presets")).contains(own).doesNotContain(theirs);
        assertThat(ids(analystA, "/api/presets?allUsers=true")).contains(own).doesNotContain(theirs);
        assertThat(ids(analystB, "/api/presets")).contains(theirs).doesNotContain(own);

        for (String method : List.of("PUT", "DELETE")) {
            HttpResponse<String> response =
                    analystA.send(method, "/api/presets/" + theirs, method.equals("PUT") ? body("Stolen") : null);

            assertThat(response.statusCode()).as(method).isEqualTo(404);
            assertThat(JSON.readTree(response.body()).get("errorCode").asString())
                    .isEqualTo(NOT_FOUND);
        }
        assertThat(ids(analystB, "/api/presets")).contains(theirs);
    }

    @Test
    void aPresetShowsItsOwner() throws Exception {
        JsonNode created = JSON.readTree(
                analystA.send("POST", "/api/presets", body("Mine")).body());

        assertThat(created.at("/owner/username").asString()).isEqualTo(analystAName);
        assertThat(created.at("/owner/id").asString()).isNotBlank();
    }

    @Test
    void anAdminSeesEveryonesPresetsOnRequestAndOwnOtherwise() throws Exception {
        String theirs = create(analystB, "Shared by B");

        assertThat(ids(admin, "/api/presets")).doesNotContain(theirs);
        JsonNode all = JSON.readTree(
                admin.send("GET", "/api/presets?allUsers=true", null).body());
        JsonNode found = all.valueStream()
                .filter(p -> p.get("id").asString().equals(theirs))
                .findFirst()
                .orElseThrow();
        assertThat(found.at("/owner/username").asString()).isEqualTo(analystBName);

        HttpResponse<String> renamed = admin.send("PUT", "/api/presets/" + theirs, body("Renamed by admin"));
        assertThat(renamed.statusCode()).isEqualTo(200);
        JsonNode renamedPreset = JSON.readTree(renamed.body());
        assertThat(renamedPreset.get("name").asString()).isEqualTo("Renamed by admin");
        assertThat(renamedPreset.at("/owner/username").asString()).isEqualTo(analystBName);

        assertThat(admin.send("DELETE", "/api/presets/" + theirs, null).statusCode())
                .isEqualTo(204);
        assertThat(ids(admin, "/api/presets?allUsers=true")).doesNotContain(theirs);
    }

    @Test
    void aViewerHasNoPresetsAndCannotCreateOne() throws Exception {
        create(analystA, "Not for the viewer");

        assertThat(ids(viewer, "/api/presets")).isEmpty();
        assertThat(ids(viewer, "/api/presets?allUsers=true")).isEmpty();
        assertThat(viewer.send("POST", "/api/presets", body("Nope")).statusCode())
                .isEqualTo(403);
    }

    @Test
    void presetsWithoutAnOwnerGoToTheFirstAdmin() throws Exception {
        PresetId unowned = presets.save(new Preset(PresetId.newId(), "Old", "{\"ticker\":\"NVDA\"}", null, null, null))
                .id();
        String firstAdmin = userDirectory.firstAdmin().orElseThrow().value();

        assignOwners.assignUnownedToFirstAdmin();

        JsonNode all = JSON.readTree(
                admin.send("GET", "/api/presets?allUsers=true", null).body());
        assertThat(all.valueStream())
                .allSatisfy(p -> assertThat(p.at("/owner/id").asString()).isNotBlank());
        JsonNode seeded = all.valueStream()
                .filter(p -> p.get("id").asString().equals(unowned.value()))
                .findFirst()
                .orElseThrow();
        assertThat(seeded.at("/owner/id").asString()).isEqualTo(firstAdmin);
    }

    private static String body(String name) {
        return "{\"name\":\"" + name + "\",\"values\":{\"ticker\":\"NVDA\"}}";
    }

    private static String create(TestSession client, String name) throws Exception {
        HttpResponse<String> created = client.send("POST", "/api/presets", body(name));
        assertThat(created.statusCode()).isEqualTo(201);
        return JSON.readTree(created.body()).get("id").asString();
    }

    private static List<String> ids(TestSession client, String path) throws Exception {
        HttpResponse<String> list = client.send("GET", path, null);
        assertThat(list.statusCode()).isEqualTo(200);
        return JSON.readTree(list.body())
                .valueStream()
                .map(p -> p.get("id").asString())
                .toList();
    }

    private TestSession loggedInAs(String username) throws Exception {
        TestSession client = TestSession.anonymous(port);
        assertThat(client.login(username, TestSession.PASSWORD).statusCode()).isEqualTo(200);
        return client;
    }
}
