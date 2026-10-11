package tr.girgin.backend.trading.analysis.platform.bff.controller.api.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Security: the session login of {@code /api/auth} and, with {@code platform.auth.enabled}, the
 * rule that every other {@code /api} route needs a session and every unsafe request a CSRF token.
 *
 * <p>The switch is temporary: off (the default), every request is permitted and nothing changes for
 * an existing installation. The repository, CSRF and session strategy beans are shared with the login
 * endpoint, so a login through it is stored and protected the way this chain expects.
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

    /** A login gets a new session ID (session fixation) and a new CSRF token. */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            JsonMapper jsonMapper,
            @Value("${platform.auth.enabled:false}") boolean authEnabled)
            throws Exception {
        ApiErrorWriter writer = new ApiErrorWriter(jsonMapper);
        http.logout(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(
                        handling -> handling.authenticationEntryPoint(new ApiAuthenticationEntryPoint(writer))
                                .accessDeniedHandler(new ApiAccessDeniedHandler(writer)));
        if (authEnabled) {
            http.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                    .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                    .authorizeHttpRequests(
                            requests -> requests.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout")
                                    .permitAll()
                                    .requestMatchers(HttpMethod.GET, "/api/auth/me")
                                    .permitAll()
                                    .requestMatchers("/api/openapi", "/api/openapi/**", "/actuator/**")
                                    .permitAll()
                                    .requestMatchers("/api/**")
                                    .authenticated()
                                    .anyRequest()
                                    .permitAll());
        } else {
            http.csrf(AbstractHttpConfigurer::disable)
                    .headers(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
        }
        return http.build();
    }
}
