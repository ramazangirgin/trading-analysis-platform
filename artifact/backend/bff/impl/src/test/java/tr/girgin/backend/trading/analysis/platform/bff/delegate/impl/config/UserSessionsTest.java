package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

class UserSessionsTest {

    private static final UserId USER = new UserId("u1");

    private final SessionRegistry registry = mock(SessionRegistry.class);
    private final SessionEventStreams streams = new SessionEventStreams();
    private final UserSessions userSessions = new UserSessions(registry, streams);

    private final SessionInformation first = new SessionInformation("u1", "s1", new Date());
    private final SessionInformation second = new SessionInformation("u1", "s2", new Date());
    private final SessionInformation otherUser = new SessionInformation("u2", "s3", new Date());

    private final SseEmitter firstStream = mock(SseEmitter.class);
    private final SseEmitter secondStream = mock(SseEmitter.class);
    private final SseEmitter otherStream = mock(SseEmitter.class);

    UserSessionsTest() {
        when(registry.getAllSessions("u1", false)).thenReturn(List.of(first, second));
        streams.register("s1", firstStream);
        streams.register("s2", secondStream);
        streams.register("s3", otherStream);
    }

    @Test
    void endAllExpiresEverySessionOfTheUserAndClosesItsStreams() {
        userSessions.endAll(USER);

        assertThat(first.isExpired()).isTrue();
        assertThat(second.isExpired()).isTrue();
        assertThat(otherUser.isExpired()).isFalse();
        verify(firstStream).complete();
        verify(secondStream).complete();
        verify(otherStream, never()).complete();
    }

    @Test
    void endAllExceptKeepsTheNamedSessionAndItsStreams() {
        userSessions.endAllExcept(USER, "s1");

        assertThat(first.isExpired()).isFalse();
        assertThat(second.isExpired()).isTrue();
        verify(firstStream, never()).complete();
        verify(secondStream).complete();
    }

    @Test
    void endAllExceptWithoutASessionIdEndsEverySession() {
        userSessions.endAllExcept(USER, null);

        assertThat(first.isExpired()).isTrue();
        assertThat(second.isExpired()).isTrue();
    }
}
