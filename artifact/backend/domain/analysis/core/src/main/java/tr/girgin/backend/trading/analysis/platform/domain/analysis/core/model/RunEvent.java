package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.time.Instant;
import java.util.Map;

/**
 * One runner event. The fields the platform acts on are typed ({@code rating} on DECISION,
 * {@code outcome} and {@code error} on RUN_FINISHED, {@code stats} on STATS; null elsewhere).
 * {@code payload} keeps every protocol field as sent, for the UI.
 */
public record RunEvent(
        long seq,
        Instant timestamp,
        RunEventType type,
        Rating rating,
        RunOutcome outcome,
        RunStats stats,
        String error,
        Map<String, Object> payload) {}
