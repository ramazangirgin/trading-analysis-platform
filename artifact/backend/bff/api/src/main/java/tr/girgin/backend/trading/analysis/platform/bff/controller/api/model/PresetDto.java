package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.Instant;
import java.util.Map;

/** Saved New Analysis form values; {@code values} is whatever the form stored. */
public record PresetDto(String id, String name, Map<String, Object> values, Instant updatedAt) {}
