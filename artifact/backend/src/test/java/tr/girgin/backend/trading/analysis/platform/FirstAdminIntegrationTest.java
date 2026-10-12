package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.CreateFirstAdminUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/** The first start on an empty database, without a configured password: a generated one is logged once. */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class FirstAdminIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern GENERATED = Pattern.compile("with the generated password (\\S+)\\. It is shown");

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private CreateFirstAdminUseCase createFirstAdmin;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestPlatformHome.register(registry, HOME, HOME.resolve("runner.sh"));
        registry.add("platform.admin.password", () -> "");
    }

    @Test
    void createsAnAdminWithAGeneratedPasswordLoggedOnceAndANoOpOnTheSecondCall(CapturedOutput output) throws Exception {
        assertThat(countLines(output)).isEqualTo(1);
        Matcher matcher = GENERATED.matcher(output.getAll());
        assertThat(matcher.find()).isTrue();
        String generated = matcher.group(1);

        TestSession session = TestSession.anonymous(port);
        HttpResponse<String> login = session.login("admin", generated);
        assertThat(login.statusCode()).isEqualTo(200);
        JsonNode me = JSON.readTree(session.send("GET", "/api/auth/me", null).body());
        assertThat(me.get("username").asString()).isEqualTo("admin");
        assertThat(me.get("roles")).extracting(JsonNode::asString).containsExactly("ADMIN");
        assertThat(me.get("permissions"))
                .extracting(JsonNode::asString)
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(Permission.values()).map(Permission::key).toList());
        assertThat(me.get("mustChangePassword").asBoolean()).isTrue();

        User before = users.findByUsername(new Username("admin")).orElseThrow();
        assertThat(before.passwordHash().value()).doesNotContain(generated);

        createFirstAdmin.createFirstAdminIfNoUser();

        assertThat(users.findByUsername(new Username("admin"))).contains(before);
        assertThat(users.count()).isEqualTo(1);
        assertThat(countLines(output)).isEqualTo(1);
    }

    private static long countLines(CapturedOutput output) {
        return output.getAll()
                .lines()
                .filter(line -> line.contains("generated password"))
                .count();
    }
}
