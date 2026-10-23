package tr.girgin.backend.trading.analysis.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.library.persistence.TestDatabaseConfiguration;

/**
 * The guard against a forgotten endpoint: every {@code /api} handler method of the application is sent a
 * request. A user without a role is refused (403) everywhere except on the open routes, and an admin is
 * refused nowhere, so an endpoint without a permission ({@code @RequiresPermission} on its delegate method)
 * fails the build. The Checkstyle rule {@code TAP-A1} catches a delegate method with neither
 * {@code @RequiresPermission} nor {@code @NoPermissionRequired} at build time already.
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

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @LocalServerPort
    private int port;

    @Autowired
    private RoleRepositoryPort roles;

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
            if (!OPEN.contains(endpoint.toString()) && statusOf(session, endpoint, true) != 403) {
                notRefused.add(endpoint.toString());
            }
        }

        assertThat(notRefused).as("endpoints a user without roles may call").isEmpty();
    }

    @Test
    void aCustomRoleWithOnlyAnalysisReadGivesExactlyThatAccess() throws Exception {
        RoleId role = new RoleId(UUID.randomUUID().toString());
        roles.save(new Role(
                role, "reader-" + role.value(), false, "reads analyses", Set.of(Permission.ANALYSIS_READ), null));
        TestSession session = TestSession.loggedIn(port, users, hasher, role);

        Set<String> notRefused = new TreeSet<>();
        for (Endpoint endpoint : endpoints()) {
            // The auth routes would end or change the session, and are open anyway.
            if (!endpoint.path().startsWith("/api/auth/") && statusOf(session, endpoint, true) != 403) {
                notRefused.add(endpoint.method() + " " + endpoint.path().replaceAll(UUID_PATTERN, "{id}"));
            }
        }

        Set<String> expected = new TreeSet<>(List.of("GET /api/openapi", "GET /api/openapi.yaml"));
        expected.addAll(List.of(
                "GET /api/analyses",
                "GET /api/analyses/{id}",
                "GET /api/analyses/{id}/report",
                "GET /api/analyses/{id}/prices",
                "GET /api/analyses/{id}/logs",
                "GET /api/analyses/{id}/events",
                "PUT /api/analyses/{id}/visibility"));
        assertThat(notRefused).isEqualTo(expected);
    }

    @Test
    void anAdminIsRefusedOnNoEndpoint() throws Exception {
        TestSession session = TestSession.loggedIn(port, users, hasher, RoleId.ADMIN);

        List<String> refused = new ArrayList<>();
        for (Endpoint endpoint : endpoints()) {
            // The auth routes would end or change the session, and are open anyway.
            if (!endpoint.path().startsWith("/api/auth/") && statusOf(session, endpoint, false) == 403) {
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

    // A valid body only where a refusal is expected: an admin must not start real analyses, and an empty body
    // answers 400 (not 403) for it.
    private static int statusOf(TestSession session, Endpoint endpoint, boolean validBody)
            throws IOException, InterruptedException {
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
        String body = validBody ? TestSession.validBody(endpoint.method(), endpoint.path()) : "{}";
        return session.send(endpoint.method(), endpoint.path(), hasBody ? body : null)
                .statusCode();
    }

    private record Endpoint(String method, String path, boolean stream) {

        @Override
        public String toString() {
            return method + " " + path;
        }
    }
}
