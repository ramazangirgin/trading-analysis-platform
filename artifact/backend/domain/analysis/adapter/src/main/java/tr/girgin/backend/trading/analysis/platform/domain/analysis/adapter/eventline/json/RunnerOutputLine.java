package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.json;

import java.util.Map;

/**
 * One parsed runner protocol line, still permissive: values are as sent. The typed fields are
 * set only on the event types that define them (docs/event-protocol.md, section 4); everything
 * except the envelope is also kept in {@code payload}, verbatim, for the UI.
 */
public record RunnerOutputLine(
        Long seq,
        String ts,
        String type,
        String rating,
        String status,
        String error,
        Long llmCalls,
        Long toolCalls,
        Long tokensIn,
        Long tokensOut,
        Double costUsd,
        Double elapsedS,
        Map<String, Object> payload) {
}
