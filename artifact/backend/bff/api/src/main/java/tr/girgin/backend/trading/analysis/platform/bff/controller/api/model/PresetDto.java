package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.Instant;
import java.util.Map;

/**
 * Saved New Analysis form values; {@code values} is whatever the form stored. {@code version} counts the
 * saves; send it back on an update to fail if someone else changed the preset meanwhile.
 */
public record PresetDto(String id, String name, Map<String, Object> values, Instant updatedAt, Long version) {}
