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
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

/**
 * A client of a running platform with {@code platform.auth.enabled=true}, as the SPA behaves: it keeps the cookies
 * the server sets and echoes the {@code XSRF-TOKEN} cookie in the {@code X-XSRF-TOKEN} header of every request.
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

    private TestSession(int port) {
        this.port = port;
    }

    // A client without a session that has read the CSRF cookie, as a page does before it shows the login form.
    static TestSession anonymous(int port) throws IOException, InterruptedException {
        TestSession session = new TestSession(port);
        session.send(session.builder("/api/auth/me").GET());
        return session;
    }

    // A client logged in as a new enabled user.
    static TestSession loggedIn(int port, UserRepositoryPort users, PasswordHasherPort hasher)
            throws IOException, InterruptedException {
        String username = createUser(users, hasher, true);
        TestSession session = anonymous(port);
        HttpResponse<String> login = session.login(username, PASSWORD);
        if (login.statusCode() != 200) {
            throw new AssertionError("Login of " + username + " answered " + login.statusCode() + ": " + login.body());
        }
        return session;
    }

    /** Stores a user with a unique username and {@link #PASSWORD}; returns the username. */
    static String createUser(UserRepositoryPort users, PasswordHasherPort hasher, boolean enabled) {
        String username = "user-" + UUID.randomUUID();
        users.save(new User(
                UserId.newId(),
                new Username(username),
                hasher.hash(PASSWORD),
                enabled,
                false,
                0,
                Optional.empty(),
                null,
                null,
                Set.of(),
                null));
        return username;
    }

    HttpResponse<String> login(String username, String password) throws IOException, InterruptedException {
        String body = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
        HttpResponse<String> response = send(build("POST", "/api/auth/login", body));
        if (response.statusCode() == 200) {
            // The login renews the CSRF token and expires the old cookie; the next response sets the new one.
            send("GET", "/api/auth/me", null);
        }
        return response;
    }

    String cookie(String name) {
        return cookies.get(name);
    }

    /** Puts a cookie on this client, e.g. a session ID the server has since ended. */
    void setCookie(String name, String value) {
        cookies.put(name, value);
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
