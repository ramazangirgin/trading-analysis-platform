package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.AuthApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ChangePasswordRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CurrentUserDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.LoginRequest;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.PasswordChangeRequiredFilter;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.SessionEventStreams;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error.IdentityExceptionToApiExceptionMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.AuthenticateUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.ChangePasswordUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.AuthenticatedUser;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

/**
 * The login, as Spring Security documents it for a custom endpoint: authenticate, build the
 * authentication, apply the session strategy, save the context. The password check itself is the
 * identity domain's.
 */
// ClassFanOutComplexity: the auth API's delegate, with all its session and security collaborators.
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
@Service
class AuthApiDelegateImpl implements AuthApiDelegate {

    private static final String SESSION_COOKIE = "JSESSIONID";
    private static final String ROLE_PREFIX = "ROLE_";

    private final AuthenticateUseCase authentication;
    private final ChangePasswordUseCase changePassword;
    private final SessionRegistry sessionRegistry;
    private final SessionEventStreams sessionEventStreams;
    private final IdentityExceptionToApiExceptionMapper errorMapper;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextLogoutHandler contextLogout = new SecurityContextLogoutHandler();
    private final CookieClearingLogoutHandler sessionCookieLogout = new CookieClearingLogoutHandler(SESSION_COOKIE);

    @SuppressWarnings("checkstyle:ParameterNumber") // constructor injection of the collaborators above
    AuthApiDelegateImpl(
            AuthenticateUseCase authentication,
            ChangePasswordUseCase changePassword,
            SessionRegistry sessionRegistry,
            SessionEventStreams sessionEventStreams,
            IdentityExceptionToApiExceptionMapper errorMapper,
            SessionAuthenticationStrategy sessionStrategy,
            SecurityContextRepository contextRepository,
            CsrfTokenRepository csrfTokenRepository) {
        this.authentication = authentication;
        this.changePassword = changePassword;
        this.sessionRegistry = sessionRegistry;
        this.sessionEventStreams = sessionEventStreams;
        this.errorMapper = errorMapper;
        this.sessionStrategy = sessionStrategy;
        this.contextRepository = contextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @Override
    public ResponseEntity<CurrentUserDto> login(
            LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        AuthenticatedUser user;
        try {
            user = authentication.authenticate(request.username(), request.password());
        } catch (IdentityException e) {
            throw errorMapper.map(e);
        }
        startSession(user, http, response);
        return ResponseEntity.ok(toDto(user));
    }

    @Override
    public ResponseEntity<CurrentUserDto> changePassword(
            ChangePasswordRequest request, HttpServletRequest http, HttpServletResponse response) {
        Optional<UserId> id = currentUserId();
        if (id.isEmpty()) {
            throw unauthorized();
        }
        AuthenticatedUser user;
        try {
            user = changePassword.changePassword(id.get(), request.currentPassword(), request.newPassword());
        } catch (IdentityException e) {
            if (e.error() == IdentityError.BAD_CREDENTIALS) {
                // The user was disabled or deleted since the login, as in me.
                endSession(http, response);
                throw unauthorized();
            }
            throw errorMapper.map(e);
        }
        String currentSessionId = http.getSession().getId();
        sessionRegistry.getAllSessions(id.get().value(), false).stream()
                .filter(session -> !session.getSessionId().equals(currentSessionId))
                .forEach(session -> {
                    // An open stream never makes another request, so expireNow alone would not end it.
                    sessionEventStreams.close(session.getSessionId());
                    session.expireNow();
                });
        // The same steps as a login: a new session ID and CSRF token, and the authorities without the mark.
        startSession(user, http, response);
        return ResponseEntity.ok(toDto(user));
    }

    private void startSession(AuthenticatedUser user, HttpServletRequest http, HttpServletResponse response) {
        Authentication token =
                UsernamePasswordAuthenticationToken.authenticated(user.id().value(), null, authorities(user));
        sessionStrategy.onAuthentication(token, http, response);
        // The strategy expires the old CSRF cookie and only defers the new token: read it so this response sets it.
        if (http.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrf) {
            csrf.getToken();
        }
        SecurityContext context = holderStrategy.createEmptyContext();
        context.setAuthentication(token);
        holderStrategy.setContext(context);
        contextRepository.saveContext(context, http, response);
    }

    @Override
    public ResponseEntity<CurrentUserDto> me(HttpServletRequest http, HttpServletResponse response) {
        Optional<UserId> id = currentUserId();
        if (id.isEmpty()) {
            // Keeps the CSRF cookie that the filter chain just set, so a client can log in next.
            throw unauthorized();
        }
        Optional<AuthenticatedUser> user = authentication.findActiveUser(id.get());
        if (user.isEmpty()) {
            // The user was disabled or deleted since the login: never answer with a stale user.
            endSession(http, response);
            throw unauthorized();
        }
        return ResponseEntity.ok(toDto(user.get()));
    }

    @Override
    public ResponseEntity<Void> logout(HttpServletRequest http, HttpServletResponse response) {
        endSession(http, response);
        return ResponseEntity.noContent().build();
    }

    private Optional<UserId> currentUserId() {
        Authentication current = holderStrategy.getContext().getAuthentication();
        if (current == null
                || current instanceof AnonymousAuthenticationToken
                || !current.isAuthenticated()
                || !(current.getPrincipal() instanceof String id)) {
            return Optional.empty();
        }
        return Optional.of(new UserId(id));
    }

    /** Invalidates the session, clears the security context and expires the session and CSRF cookies. */
    private void endSession(HttpServletRequest http, HttpServletResponse response) {
        Authentication current = holderStrategy.getContext().getAuthentication();
        contextLogout.logout(http, response, current);
        sessionCookieLogout.logout(http, response, current);
        csrfTokenRepository.saveToken(null, http, response);
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required", Map.of());
    }

    private static List<GrantedAuthority> authorities(AuthenticatedUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        user.roleNames().forEach(name -> authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + name)));
        user.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission.key())));
        if (user.mustChangePassword()) {
            authorities.add(new SimpleGrantedAuthority(PasswordChangeRequiredFilter.AUTHORITY));
        }
        return authorities;
    }

    private static CurrentUserDto toDto(AuthenticatedUser user) {
        return new CurrentUserDto(
                user.id().value(),
                user.username().value(),
                user.roleNames().stream().sorted().toList(),
                user.permissions().stream()
                        .map(Permission::key)
                        .sorted(Comparator.naturalOrder())
                        .toList(),
                user.mustChangePassword());
    }
}
