package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.event.EventListener;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.security.web.session.HttpSessionIdChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The open event streams, one set per session ID, so a stream can be closed when its session ends.
 *
 * <p>The {@link HttpSessionEventPublisher} raises an {@link HttpSessionDestroyedEvent} when a session
 * is invalidated (a logout, the idle timeout) and an {@link HttpSessionIdChangedEvent} when a login
 * or a password change moves the session to a new ID; the streams follow it. A session that is only
 * marked expired, as a password change marks the user's other sessions, never makes another request
 * to be invalidated on, so the caller closes its streams directly.
 */
@Component
public final class SessionEventStreams {

    private final ConcurrentMap<String, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    /** Adds the emitter to the session's streams; it leaves the set again when the emitter ends. */
    public void register(String sessionId, SseEmitter emitter) {
        streams.computeIfAbsent(sessionId, _ -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(_ -> remove(sessionId, emitter));
    }

    /** Ends every open stream of the session, without an {@code end} event: the run may go on. */
    public void close(String sessionId) {
        Set<SseEmitter> emitters = streams.remove(sessionId);
        if (emitters != null) {
            emitters.forEach(SseEmitter::complete);
        }
    }

    @EventListener
    void onSessionDestroyed(HttpSessionDestroyedEvent event) {
        close(event.getId());
    }

    @EventListener
    void onSessionIdChanged(HttpSessionIdChangedEvent event) {
        Set<SseEmitter> emitters = streams.remove(event.getOldSessionId());
        if (emitters != null) {
            streams.computeIfAbsent(event.getNewSessionId(), _ -> ConcurrentHashMap.newKeySet())
                    .addAll(emitters);
        }
    }

    private void remove(String sessionId, SseEmitter emitter) {
        streams.computeIfPresent(sessionId, (_, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
