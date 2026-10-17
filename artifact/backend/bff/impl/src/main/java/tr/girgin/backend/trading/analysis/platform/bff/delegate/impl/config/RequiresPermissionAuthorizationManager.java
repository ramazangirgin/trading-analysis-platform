package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import java.util.Arrays;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;

/** Grants a call when the authentication holds any permission of the method's {@link RequiresPermission}. */
class RequiresPermissionAuthorizationManager implements AuthorizationManager<MethodInvocation> {

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication, MethodInvocation invocation) {
        RequiresPermission required = AnnotationUtils.findAnnotation(invocation.getMethod(), RequiresPermission.class);
        Authentication current = authentication.get();
        if (required == null
                || current == null
                || !current.isAuthenticated()
                || current instanceof AnonymousAuthenticationToken) {
            return new AuthorizationDecision(false);
        }
        boolean granted = current.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority ->
                        Arrays.stream(required.value()).map(Permission::key).anyMatch(authority::equals));
        return new AuthorizationDecision(granted);
    }
}
