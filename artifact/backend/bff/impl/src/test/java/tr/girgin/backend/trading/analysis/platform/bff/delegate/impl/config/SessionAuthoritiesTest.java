package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.GrantedAuthority;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.AuthenticatedUser;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

class SessionAuthoritiesTest {

    private final SessionAuthorities sessionAuthorities = new SessionAuthorities();

    private static AuthenticatedUser user(boolean mustChangePassword) {
        return new AuthenticatedUser(
                new UserId(UUID.randomUUID().toString()),
                new Username("ada"),
                Set.of("ANALYST"),
                Set.of(Permission.ANALYSIS_READ),
                mustChangePassword);
    }

    private static Set<String> names(Iterable<? extends GrantedAuthority> authorities) {
        Set<String> names = new HashSet<>();
        authorities.forEach(a -> names.add(a.getAuthority()));
        return names;
    }

    @Test
    void theAuthoritiesAreTheRolesAndThePermissionKeys() {
        assertThat(names(sessionAuthorities.authorities(user(false))))
                .containsExactlyInAnyOrder("ROLE_ANALYST", Permission.ANALYSIS_READ.key());
    }

    @Test
    void aUserWhoMustChangeThePasswordGetsTheMark() {
        assertThat(names(sessionAuthorities.authorities(user(true)))).contains(PasswordChangeRequiredFilter.AUTHORITY);
    }

    @Test
    void aSessionStampedBeforeARoleChangeIsStaleAndOneStampedAfterIsNot() {
        MockHttpSession session = new MockHttpSession();
        sessionAuthorities.stamp(session, sessionAuthorities.generation());
        assertThat(sessionAuthorities.isStale(session)).isFalse();

        sessionAuthorities.rolesChanged();
        assertThat(sessionAuthorities.isStale(session)).isTrue();

        sessionAuthorities.stamp(session, sessionAuthorities.generation());
        assertThat(sessionAuthorities.isStale(session)).isFalse();
    }

    @Test
    void aSessionWithoutAStampIsStale() {
        assertThat(sessionAuthorities.isStale(new MockHttpSession())).isTrue();
    }
}
