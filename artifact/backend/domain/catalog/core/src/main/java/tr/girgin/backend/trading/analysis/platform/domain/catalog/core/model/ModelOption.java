package tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model;

import java.util.Objects;

/** A model a provider offers, as upstream's CLI lists it. */
public record ModelOption(String id, String label) {

    public ModelOption {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
    }
}
