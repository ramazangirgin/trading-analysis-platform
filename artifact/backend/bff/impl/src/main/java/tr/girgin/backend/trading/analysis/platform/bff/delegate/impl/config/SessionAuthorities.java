package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.AuthenticatedUser;

/**
 * The authorities of a session and when they are out of date. A session is stamped with the generation
 * its authorities were built at; {@link #rolesChanged()} starts a new generation, so every session built
 * before it is stale. In memory like the sessions: the platform runs as one instance.
 */
@Component
public class SessionAuthorities {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String GENERATION_ATTRIBUTE = SessionAuthorities.class.getName() + ".GENERATION";

    private final AtomicLong generation = new AtomicLong();

    public void rolesChanged() {
        generation.incrementAndGet();
    }

    public long generation() {
        return generation.get();
    }

    public List<GrantedAuthority> authorities(AuthenticatedUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        user.roleNames().forEach(name -> authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + name)));
        user.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission.key())));
        if (user.mustChangePassword()) {
            authorities.add(new SimpleGrantedAuthority(PasswordChangeRequiredFilter.AUTHORITY));
        }
        return authorities;
    }

    /** Stamps with the generation read before the authorities were built, so a change in between is not lost. */
    public void stamp(HttpSession session, long builtAt) {
        session.setAttribute(GENERATION_ATTRIBUTE, builtAt);
    }

    public boolean isStale(HttpSession session) {
        return !(session.getAttribute(GENERATION_ATTRIBUTE) instanceof Long builtAt) || builtAt < generation.get();
    }
}
