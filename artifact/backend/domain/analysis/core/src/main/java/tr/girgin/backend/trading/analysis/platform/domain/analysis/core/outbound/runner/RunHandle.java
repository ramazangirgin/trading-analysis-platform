package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner;

import java.util.Objects;

/** Runner-specific reference to a started run (process id, container id). */
public record RunHandle(String ref) {

    public RunHandle {
        Objects.requireNonNull(ref, "ref");
    }
}
