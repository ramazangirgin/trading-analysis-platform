package tr.girgin.backend.trading.analysis.platform.domain.settings.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Saved New Analysis form values. The payload is the frontend's JSON, stored as is: the settings
 * domain does not interpret analysis specs.
 *
 * @param updatedAt when the preset was last saved. An audit field: persistence sets it, so it is {@code null} on a
 *     preset the core builds before its first save, and set on every preset the repository returns.
 * @param version the optimistic lock. Owned by persistence: {@code null} on a preset the core builds before its
 *     first save, set on every preset the repository returns, and never changed by the core. A save of a preset
 *     whose version differs from the stored one fails.
 */
public record Preset(PresetId id, String name, String payload, Instant updatedAt, Long version) {

    public Preset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(payload, "payload");
    }
}
