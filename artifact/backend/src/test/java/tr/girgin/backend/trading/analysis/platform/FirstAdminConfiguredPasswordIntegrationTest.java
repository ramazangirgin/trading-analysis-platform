package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/** The first start with {@code platform.admin.password}: that password logs in, and nothing is logged about it. */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class FirstAdminConfiguredPasswordIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "configured-first-admin-secret";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepositoryPort users;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestPlatformHome.register(registry, HOME, HOME.resolve("runner.sh"));
        registry.add("platform.admin.password", () -> PASSWORD);
    }

    @Test
    void theConfiguredPasswordLogsInAndIsNeverLogged(CapturedOutput output) throws Exception {
        TestSession session = TestSession.anonymous(port);

        assertThat(session.login("admin", PASSWORD).statusCode()).isEqualTo(200);
        JsonNode me = JSON.readTree(session.send("GET", "/api/auth/me", null).body());
        assertThat(me.get("roles")).extracting(JsonNode::asString).containsExactly("ADMIN");
        assertThat(me.get("mustChangePassword").asBoolean()).isFalse();
        assertThat(users.count()).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain("generated password");
    }
}
