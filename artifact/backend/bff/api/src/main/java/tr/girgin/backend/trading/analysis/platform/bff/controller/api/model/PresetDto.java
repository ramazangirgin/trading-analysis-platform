package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.Instant;

/**
 * A saved New Analysis form. {@code version} counts the saves; send it back on an update to fail if someone else
 * changed the preset meanwhile.
 */
public record PresetDto(
        String id, String name, PresetValuesDto values, PresetOwnerDto owner, Instant updatedAt, Long version) {}
