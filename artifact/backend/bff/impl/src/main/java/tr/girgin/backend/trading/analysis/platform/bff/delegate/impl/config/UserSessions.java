package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

/**
 * Ends the sessions of a user. The next request of an ended session gets 401 "Session ended" from the
 * concurrency filter; its open event streams are closed at once, because a stream never makes another request.
 */
@Component
public class UserSessions {

    private final SessionRegistry sessionRegistry;
    private final SessionEventStreams sessionEventStreams;

    UserSessions(SessionRegistry sessionRegistry, SessionEventStreams sessionEventStreams) {
        this.sessionRegistry = sessionRegistry;
        this.sessionEventStreams = sessionEventStreams;
    }

    public void endAll(UserId user) {
        endExcept(user, null);
    }

    public void endAllExcept(UserId user, String sessionId) {
        endExcept(user, sessionId);
    }

    private void endExcept(UserId user, String keptSessionId) {
        sessionRegistry.getAllSessions(user.value(), false).stream()
                .filter(session -> !session.getSessionId().equals(keptSessionId))
                .forEach(session -> {
                    sessionEventStreams.close(session.getSessionId());
                    session.expireNow();
                });
    }
}
