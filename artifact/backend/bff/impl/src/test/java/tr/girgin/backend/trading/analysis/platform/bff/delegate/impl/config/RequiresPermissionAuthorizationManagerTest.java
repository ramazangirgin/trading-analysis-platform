package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.util.SimpleMethodInvocation;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;

class RequiresPermissionAuthorizationManagerTest {

    private final RequiresPermissionAuthorizationManager manager = new RequiresPermissionAuthorizationManager();

    private boolean granted(Authentication authentication, String method) throws Exception {
        SimpleMethodInvocation invocation =
                new SimpleMethodInvocation(new Endpoints(), Endpoints.class.getMethod(method));
        return manager.authorize(() -> authentication, invocation).isGranted();
    }

    private static Authentication user(String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated(
                "user", null, AuthorityUtils.createAuthorityList(authorities));
    }

    @Test
    void anyOneOfTheRequiredPermissionsGrantsTheCall() throws Exception {
        assertThat(granted(user("settings:read"), "runOrRead")).isTrue();
        assertThat(granted(user("analysis:run", "preset:read"), "runOrRead")).isTrue();
    }

    @Test
    void aUserWithoutTheRequiredPermissionsIsDenied() throws Exception {
        assertThat(granted(user("analysis:read", "ROLE_VIEWER"), "runOrRead")).isFalse();
        assertThat(granted(user(), "runOrRead")).isFalse();
    }

    @Test
    void anAnonymousAuthenticationIsDenied() throws Exception {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("analysis:run")));
        assertThat(granted(anonymous, "runOrRead")).isFalse();
    }

    @Test
    void aMissingAuthenticationIsDenied() throws Exception {
        assertThat(granted(null, "runOrRead")).isFalse();
    }

    @Test
    void aMethodWithoutTheAnnotationIsDenied() throws Exception {
        assertThat(granted(user("analysis:run"), "unannotated")).isFalse();
    }

    @SuppressWarnings("unused")
    static class Endpoints {

        @RequiresPermission({Permission.ANALYSIS_RUN, Permission.SETTINGS_READ})
        public void runOrRead() {}

        public void unannotated() {}
    }
}
