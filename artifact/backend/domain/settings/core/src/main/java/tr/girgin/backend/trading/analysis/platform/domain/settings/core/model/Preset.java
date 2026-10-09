package tr.girgin.backend.trading.analysis.platform.domain.settings.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Saved New Analysis form values. The payload is the frontend's JSON, stored as is: the settings
 * domain does not interpret analysis specs.
 *
 * @param updatedAt when the preset was last saved. An audit field: persistence sets it, so it is {@code null} on a
 *     preset the core builds before its first save, and set on every preset the repository returns.
 */
public record Preset(PresetId id, String name, String payload, Instant updatedAt) {

    public Preset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(payload, "payload");
    }
}
