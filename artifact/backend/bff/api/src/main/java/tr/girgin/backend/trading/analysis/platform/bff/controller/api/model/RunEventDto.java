package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.Instant;
import java.util.Map;

/**
 * One runner event as sent over SSE. {@code type} is the protocol's wire name
 * (docs/event-protocol.md); {@code payload} holds its fields as the runner sent them.
 */
public record RunEventDto(long seq, Instant timestamp, String type, Map<String, Object> payload) {
}
