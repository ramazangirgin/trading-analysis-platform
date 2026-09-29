package tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model;

import java.util.Objects;

public record AnalystOption(String id, String agent) {

    public AnalystOption {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(agent, "agent");
    }
}
