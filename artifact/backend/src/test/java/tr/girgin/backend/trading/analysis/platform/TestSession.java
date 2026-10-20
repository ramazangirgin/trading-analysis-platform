package tr.girgin.backend.trading.analysis.platform;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

/**
 * A client of a running platform, as the SPA behaves: it keeps the cookies the server sets and echoes the
 * {@code XSRF-TOKEN} cookie in the {@code X-XSRF-TOKEN} header of every request.
 */
final class TestSession {

    static final String PASSWORD = "correct horse battery staple";
    static final String SESSION_COOKIE = "JSESSIONID";
    static final String CSRF_COOKIE = "XSRF-TOKEN";
    static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private static final String JSON = "application/json";

    private final int port;
    private final HttpClient http = HttpClient.newHttpClient();
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final Map<String, String> headers = new LinkedHashMap<>();

    private TestSession(int port) {
        this.port = port;
    }

    // A client without a session that has read the CSRF cookie, as a page does before it shows the login form.
    static TestSession anonymous(int port) throws IOException, InterruptedException {
        TestSession session = new TestSession(port);
        session.send(session.builder("/api/auth/me").GET());
        return session;
    }

    // A client logged in as a new enabled user with the given roles (none: a user without any permission).
    static TestSession loggedIn(int port, UserRepositoryPort users, PasswordHasherPort hasher, RoleId... roles)
            throws IOException, InterruptedException {
        String username = createUser(users, hasher, true, roles);
        TestSession session = anonymous(port);
        HttpResponse<String> login = session.login(username, PASSWORD);
        if (login.statusCode() != 200) {
            throw new AssertionError("Login of " + username + " answered " + login.statusCode() + ": " + login.body());
        }
        return session;
    }

    /** Stores a user with the given roles, a unique username and {@link #PASSWORD}; returns the username. */
    static String createUser(UserRepositoryPort users, PasswordHasherPort hasher, boolean enabled, RoleId... roles) {
        return storeUser(users, hasher, enabled, false, roles);
    }

    /** Stores an enabled user with a unique username and {@link #PASSWORD} who must change it; returns the username. */
    static String createUserWhoMustChangePassword(
            UserRepositoryPort users, PasswordHasherPort hasher, RoleId... roles) {
        return storeUser(users, hasher, true, true, roles);
    }

    private static String storeUser(
            UserRepositoryPort users,
            PasswordHasherPort hasher,
            boolean enabled,
            boolean mustChangePassword,
            RoleId... roles) {
        String username = "user-" + UUID.randomUUID();
        users.save(new User(
                UserId.newId(),
                new Username(username),
                hasher.hash(PASSWORD),
                enabled,
                mustChangePassword,
                0,
                Optional.empty(),
                Optional.empty(),
                null,
                null,
                Set.of(roles),
                null));
        return username;
    }

    /**
     * A body the request validation accepts for the endpoint, so a refusal can only come from the permission
     * check: bean validation runs before the delegate method (and its permission) is called.
     */
    static String validBody(String method, String path) {
        String body = "{}";
        if (method.equals("POST") && path.equals("/api/analyses")) {
            body = "{\"ticker\":\"AAPL\",\"tradeDate\":\"2026-01-02\",\"analysts\":[\"MARKET\"],"
                    + "\"llmProvider\":\"openai\",\"deepThinkLlm\":\"deep\",\"quickThinkLlm\":\"quick\"}";
        } else if (method.equals("PUT") && path.startsWith("/api/secrets/")) {
            body = "{\"value\":\"secret\"}";
        } else if (method.equals("POST") && path.equals("/api/users")) {
            body = "{\"username\":\"someone\",\"roleIds\":[]}";
        } else if (method.equals("PATCH") && path.startsWith("/api/users/")) {
            body = "{\"enabled\":true}";
        } else if (path.startsWith("/api/settings/")) {
            body = validAnalysisDefaultsBody(method);
        } else if (path.startsWith("/api/presets")) {
            body = "{\"name\":\"preset\",\"values\":{}}";
        } else if (path.startsWith("/api/roles")) {
            body = validRoleBody(method);
        }
        return body;
    }

    private static String validAnalysisDefaultsBody(String method) {
        if (!method.equals("PUT")) {
            return "{}";
        }
        return "{\"llmProvider\":\"openai\",\"deepThinkLlm\":\"deep\",\"quickThinkLlm\":\"quick\","
                + "\"maxDebateRounds\":1,\"maxRiskDiscussRounds\":1}";
    }

    private static String validRoleBody(String method) {
        return switch (method) {
            case "POST" -> "{\"name\":\"someone\",\"permissions\":[]}";
            case "PATCH" -> "{\"description\":\"changed\"}";
            default -> "{}";
        };
    }

    HttpResponse<String> login(String username, String password) throws IOException, InterruptedException {
        String body = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
        return send(build("POST", "/api/auth/login", body));
    }

    HttpResponse<String> changePassword(String currentPassword, String newPassword)
            throws IOException, InterruptedException {
        String body = "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(currentPassword, newPassword);
        return send(build("POST", "/api/auth/password", body));
    }

    String cookie(String name) {
        return cookies.get(name);
    }

    /** Puts a cookie on this client, e.g. a session ID the server has since ended. */
    void setCookie(String name, String value) {
        cookies.put(name, value);
    }

    /** Sends this header on every following request, as a reverse proxy does; returns this client. */
    TestSession withHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    // The same client without the CSRF header, to prove the server asks for it.
    HttpResponse<String> sendWithoutCsrfHeader(String method, String path, String body)
            throws IOException, InterruptedException {
        return send(build(method, path, body), HttpResponse.BodyHandlers.ofString(), false);
    }

    HttpResponse<String> send(String method, String path, String body) throws IOException, InterruptedException {
        return send(build(method, path, body));
    }

    HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return send(request, HttpResponse.BodyHandlers.ofString(), true);
    }

    // Sends with the cookies and the CSRF header; the request is built with builder(path).
    <T> HttpResponse<T> send(HttpRequest.Builder request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        return send(request, handler, true);
    }

    private <T> HttpResponse<T> send(HttpRequest.Builder request, HttpResponse.BodyHandler<T> handler, boolean csrf)
            throws IOException, InterruptedException {
        headers.forEach(request::header);
        if (!cookies.isEmpty()) {
            request.header("Cookie", cookieHeader());
        }
        if (csrf && cookies.containsKey(CSRF_COOKIE)) {
            request.header(CSRF_HEADER, cookies.get(CSRF_COOKIE));
        }
        HttpResponse<T> response = http.send(request.build(), handler);
        remember(response);
        return response;
    }

    /** A request builder for a path of this platform. */
    HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpRequest.Builder build(String method, String path, String body) {
        HttpRequest.Builder request = builder(path);
        if (body == null) {
            return request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return request.header("Content-Type", JSON).method(method, HttpRequest.BodyPublishers.ofString(body));
    }

    private String cookieHeader() {
        StringBuilder header = new StringBuilder();
        cookies.forEach((name, value) -> {
            if (!header.isEmpty()) {
                header.append("; ");
            }
            header.append(name).append('=').append(value);
        });
        return header.toString();
    }

    private void remember(HttpResponse<?> response) {
        for (String setCookie : response.headers().allValues("Set-Cookie")) {
            String[] parts = setCookie.split(";");
            int equals = parts[0].indexOf('=');
            String name = parts[0].substring(0, equals).strip();
            String value = parts[0].substring(equals + 1).strip();
            boolean expired = value.isEmpty();
            for (String attribute : parts) {
                if (attribute.strip().equalsIgnoreCase("Max-Age=0")) {
                    expired = true;
                }
            }
            if (expired) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }
}
