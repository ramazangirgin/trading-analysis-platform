package tr.girgin.backend.trading.analysis.platform.domain.settings.core.model;

import java.util.Objects;
import java.util.UUID;

public record PresetId(String value) {

    public PresetId {
        Objects.requireNonNull(value, "value");
    }

    public static PresetId newId() {
        return new PresetId(UUID.randomUUID().toString());
    }
}
