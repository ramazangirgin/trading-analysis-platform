package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Security: the session login of {@code /api/auth} and the rules that every other {@code /api}
 * route needs a session, every unsafe request a CSRF token, and an {@code ASYNC} dispatch passes: it
 * continues a request that was authorized when it started, e.g. an event stream whose session ended
 * meanwhile.
 *
 * <p>The repository, CSRF and session strategy beans are shared with the login endpoint, so a login
 * through it is stored and protected the way this chain expects.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").path("/"));
        return repository;
    }

    /** The sessions of each user (the principal is the user ID), kept in memory like the sessions themselves. */
    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Tells the registry when a session ends or gets a new ID. */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /** A login gets a new session ID (session fixation) and a new CSRF token, and is registered under its user. */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(
            CsrfTokenRepository csrfTokenRepository, SessionRegistry sessionRegistry) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository),
                new RegisterSessionAuthenticationStrategy(sessionRegistry)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            SessionRegistry sessionRegistry,
            JsonMapper jsonMapper)
            throws Exception {
        ApiErrorWriter writer = new ApiErrorWriter(jsonMapper);
        http.logout(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                // No limit of sessions per user: the registry is there to end the other sessions on demand.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionConcurrency(concurrency -> concurrency
                                .maximumSessions(-1)
                                .sessionRegistry(sessionRegistry)
                                .expiredSessionStrategy(event -> writer.write(
                                        event.getResponse(),
                                        HttpStatus.UNAUTHORIZED,
                                        ApiAuthenticationEntryPoint.ERROR_CODE,
                                        "Session ended"))))
                .exceptionHandling(
                        handling -> handling.authenticationEntryPoint(new ApiAuthenticationEntryPoint(writer))
                                .accessDeniedHandler(new ApiAccessDeniedHandler(writer)));
        http.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterAfter(new PasswordChangeRequiredFilter(writer), AuthorizationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        // An ASYNC dispatch continues a request that was authorized when it
                        // started (an event stream closed by its session's end): it must pass,
                        // or the entry point would write a 401 onto a committed response.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me")
                        .permitAll()
                        .requestMatchers("/api/openapi", "/api/openapi/**", "/actuator/**")
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll());
        return http.build();
    }
}
