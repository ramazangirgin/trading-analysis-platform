package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

/** The user of the current request, read from the session's authentication. */
@Component
public class CurrentUser {

    public UserId id() {
        return new UserId((String) authentication().getPrincipal());
    }

    public boolean has(Permission permission) {
        return authentication().getAuthorities().stream()
                .anyMatch(a -> permission.key().equals(a.getAuthority()));
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
