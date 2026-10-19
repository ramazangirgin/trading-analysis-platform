package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.AuthenticateUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.AuthenticatedUser;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

class RoleAuthoritiesRefreshFilterTest {

    private final SessionAuthorities sessionAuthorities = new SessionAuthorities();
    private final AuthenticateUseCase authentication = mock(AuthenticateUseCase.class);
    private final SecurityContextRepository repository = mock(SecurityContextRepository.class);
    private final RoleAuthoritiesRefreshFilter filter =
            new RoleAuthoritiesRefreshFilter(sessionAuthorities, authentication, repository);
    private final UserId id = UserId.newId();
    private final AtomicInteger chainCalls = new AtomicInteger();
    private final MockHttpSession session = new MockHttpSession();

    @BeforeEach
    void loggedIn() {
        sessionAuthorities.stamp(session, sessionAuthorities.generation());
        authenticate(id.value());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String principal) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_OLD"))));
        SecurityContextHolder.setContext(context);
    }

    private void run(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.setSession(session);
        filter.doFilter(request, new MockHttpServletResponse(), (_, _) -> chainCalls.incrementAndGet());
    }

    private Set<String> currentAuthorities() {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        return current.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    @Test
    void aStaleSessionGetsTheCurrentAuthoritiesAndANewStampAndTheContextIsSaved() throws Exception {
        when(authentication.findActiveUser(id))
                .thenReturn(Optional.of(new AuthenticatedUser(
                        id, new Username("ada"), Set.of("CUSTOM"), Set.of(Permission.ANALYSIS_READ), false)));
        sessionAuthorities.rolesChanged();

        run("/api/presets");

        assertThat(currentAuthorities()).containsExactlyInAnyOrder("ROLE_CUSTOM", Permission.ANALYSIS_READ.key());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(id.value());
        assertThat(sessionAuthorities.isStale(session)).isFalse();
        verify(repository).saveContext(any(), any(), any());
        assertThat(chainCalls).hasValue(1);
    }

    @Test
    void aFreshSessionIsNotLookedUp() throws Exception {
        run("/api/presets");

        verifyNoInteractions(authentication, repository);
        assertThat(currentAuthorities()).containsExactly("ROLE_OLD");
        assertThat(chainCalls).hasValue(1);
    }

    @Test
    void anInactiveUserKeepsTheOldAuthentication() throws Exception {
        when(authentication.findActiveUser(id)).thenReturn(Optional.empty());
        sessionAuthorities.rolesChanged();

        run("/api/presets");

        assertThat(currentAuthorities()).containsExactly("ROLE_OLD");
        verify(repository, never()).saveContext(any(), any(), any());
        assertThat(chainCalls).hasValue(1);
    }

    @Test
    void aRequestWithoutAnAuthenticationPassesUntouched() throws Exception {
        SecurityContextHolder.clearContext();
        sessionAuthorities.rolesChanged();

        run("/api/presets");

        verifyNoInteractions(authentication, repository);
        assertThat(chainCalls).hasValue(1);
    }

    @Test
    void aRequestOutsideApiPassesUntouched() throws Exception {
        sessionAuthorities.rolesChanged();

        run("/actuator/health");

        verifyNoInteractions(authentication, repository);
        assertThat(currentAuthorities()).containsExactly("ROLE_OLD");
        assertThat(chainCalls).hasValue(1);
    }
}
