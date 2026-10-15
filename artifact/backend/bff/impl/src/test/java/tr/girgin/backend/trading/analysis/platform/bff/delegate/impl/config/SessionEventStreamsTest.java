package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.security.web.session.HttpSessionIdChangedEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SessionEventStreamsTest {

    private final SessionEventStreams streams = new SessionEventStreams();

    @Test
    void closeCompletesEveryEmitterOfThatSessionAndNoneOfAnother() {
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        SseEmitter otherSession = mock(SseEmitter.class);
        streams.register("s1", first);
        streams.register("s1", second);
        streams.register("s2", otherSession);

        streams.close("s1");

        verify(first).complete();
        verify(second).complete();
        verify(otherSession, never()).complete();
    }

    @Test
    void aCompletedEmitterIsRemoved() {
        SseEmitter emitter = mock(SseEmitter.class);
        streams.register("s1", emitter);
        ArgumentCaptor<Runnable> onCompletion = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onCompletion(onCompletion.capture());

        onCompletion.getValue().run();
        streams.close("s1");

        verify(emitter, never()).complete();
    }

    @Test
    void aTimedOutEmitterIsRemoved() {
        SseEmitter emitter = mock(SseEmitter.class);
        streams.register("s1", emitter);
        ArgumentCaptor<Runnable> onTimeout = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onTimeout(onTimeout.capture());

        onTimeout.getValue().run();
        streams.close("s1");

        verify(emitter, never()).complete();
    }

    @Test
    void aFailedEmitterIsRemoved() {
        SseEmitter emitter = mock(SseEmitter.class);
        streams.register("s1", emitter);
        ArgumentCaptor<Consumer<Throwable>> onError = ArgumentCaptor.forClass(Consumer.class);
        verify(emitter).onError(onError.capture());

        onError.getValue().accept(new IOException("client gone"));
        streams.close("s1");

        verify(emitter, never()).complete();
    }

    @Test
    void aDestroyedSessionClosesItsEmitters() {
        SseEmitter emitter = mock(SseEmitter.class);
        streams.register("s1", emitter);
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn("s1");

        streams.onSessionDestroyed(new HttpSessionDestroyedEvent(session));

        verify(emitter).complete();
    }

    @Test
    void aSessionIdChangeMovesItsEmittersToTheNewId() {
        SseEmitter emitter = mock(SseEmitter.class);
        streams.register("s1", emitter);
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn("s2");

        streams.onSessionIdChanged(new HttpSessionIdChangedEvent(session, "s1"));

        streams.close("s2");
        verify(emitter).complete();
    }

    @Test
    void closingAnUnknownSessionDoesNothing() {
        SseEmitter emitter = mock(SseEmitter.class);
        streams.register("s1", emitter);

        streams.close("nobody");

        verify(emitter, never()).complete();
    }
}
