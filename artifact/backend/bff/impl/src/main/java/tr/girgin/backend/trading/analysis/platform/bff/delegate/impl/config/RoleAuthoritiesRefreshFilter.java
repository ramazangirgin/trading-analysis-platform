package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.AuthenticatePort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

/**
 * Gives a session the current authorities of its user when roles have changed since the session's
 * authorities were built, so a permission change applies to the next request without a new login.
 * A user who is no longer active keeps the old authentication: disabling already ends their sessions.
 */
public final class RoleAuthoritiesRefreshFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/";

    private final SessionAuthorities sessionAuthorities;
    private final AuthenticatePort authenticatePort;
    private final SecurityContextRepository securityContextRepository;

    RoleAuthoritiesRefreshFilter(
            SessionAuthorities sessionAuthorities,
            AuthenticatePort authenticatePort,
            SecurityContextRepository securityContextRepository) {
        this.sessionAuthorities = sessionAuthorities;
        this.authenticatePort = authenticatePort;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getServletPath().startsWith(API_PREFIX)) {
            refresh(request, response);
        }
        chain.doFilter(request, response);
    }

    private void refresh(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication current = context.getAuthentication();
        HttpSession session = request.getSession(false);
        if (session == null
                || current == null
                || current instanceof AnonymousAuthenticationToken
                || !current.isAuthenticated()
                || !(current.getPrincipal() instanceof String id)
                || !sessionAuthorities.isStale(session)) {
            return;
        }
        long builtAt = sessionAuthorities.generation();
        authenticatePort.findActiveUser(new UserId(id)).ifPresent(user -> {
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    current.getPrincipal(), null, sessionAuthorities.authorities(user)));
            securityContextRepository.saveContext(context, request, response);
            sessionAuthorities.stamp(session, builtAt);
        });
    }
}
