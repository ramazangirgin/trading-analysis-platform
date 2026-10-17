package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/**
 * The guard against a forgotten endpoint: every {@code /api} handler method of the application is sent a
 * request. A user without a role is refused (403) everywhere except on the open routes, and an admin is
 * refused nowhere, so an endpoint without a permission rule (it falls into {@code denyAll}) fails the build.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class EndpointPermissionsIntegrationTest {

    private static final Path HOME = TestPlatformHome.create();

    // The routes that need no permission: the login flow, a user's own password and the API description.
    private static final Set<String> OPEN = Set.of(
            "POST /api/auth/login",
            "POST /api/auth/logout",
            "GET /api/auth/me",
            "POST /api/auth/password",
            "GET /api/openapi",
            "GET /api/openapi.yaml");

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private PasswordHasherPort hasher;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestPlatformHome.register(registry, HOME, HOME.resolve("runner.sh"));
    }

    @Test
    void aUserWithoutRolesIsRefusedOnEveryEndpointButTheOpenOnes() throws Exception {
        List<Endpoint> endpoints = endpoints();
        assertThat(endpoints).hasSizeGreaterThan(15);
        TestSession session = TestSession.loggedIn(port, users, hasher);

        List<String> notRefused = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            if (!OPEN.contains(endpoint.toString()) && statusOf(session, endpoint) != 403) {
                notRefused.add(endpoint.toString());
            }
        }

        assertThat(notRefused).as("endpoints a user without roles may call").isEmpty();
    }

    @Test
    void anAdminIsRefusedOnNoEndpoint() throws Exception {
        TestSession session = TestSession.loggedIn(port, users, hasher, RoleId.ADMIN);

        List<String> refused = new ArrayList<>();
        for (Endpoint endpoint : endpoints()) {
            // The auth routes would end or change the session, and are open anyway.
            if (!endpoint.path().startsWith("/api/auth/") && statusOf(session, endpoint) == 403) {
                refused.add(endpoint.toString());
            }
        }

        assertThat(refused).as("endpoints without a permission rule").isEmpty();
    }

    private List<Endpoint> endpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            boolean stream =
                    info.getProducesCondition().getProducibleMediaTypes().contains(MediaType.TEXT_EVENT_STREAM);
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (pattern.startsWith("/api/")) {
                    assertThat(info.getMethodsCondition().getMethods())
                            .as("every /api mapping must declare its HTTP method: " + pattern)
                            .isNotEmpty();
                    info.getMethodsCondition()
                            .getMethods()
                            .forEach(method -> endpoints.add(new Endpoint(
                                    method.name(),
                                    pattern.replaceAll(
                                            "\\{[^}]+}", UUID.randomUUID().toString()),
                                    stream)));
                }
            }
        }
        return endpoints;
    }

    private static int statusOf(TestSession session, Endpoint endpoint) throws IOException, InterruptedException {
        if (endpoint.stream()) {
            // Only the status line is read: the stream of a real run would stay open.
            HttpResponse<InputStream> response = session.send(
                    session.builder(endpoint.path())
                            .header("Accept", "text/event-stream")
                            .GET(),
                    HttpResponse.BodyHandlers.ofInputStream());
            response.body().close();
            return response.statusCode();
        }
        boolean hasBody = !endpoint.method().equals("GET") && !endpoint.method().equals("DELETE");
        return session.send(endpoint.method(), endpoint.path(), hasBody ? "{}" : null)
                .statusCode();
    }

    private record Endpoint(String method, String path, boolean stream) {

        @Override
        public String toString() {
            return method + " " + path;
        }
    }
}
