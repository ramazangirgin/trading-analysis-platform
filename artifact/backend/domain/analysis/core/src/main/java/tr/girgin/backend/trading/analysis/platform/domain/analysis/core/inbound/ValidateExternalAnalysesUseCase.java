package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;

/** Checks runs found in the data dir before any of them is registered. */
public interface ValidateExternalAnalysesUseCase {

    /**
     * Throws {@link IllegalArgumentException} for the first invalid record: a run history record without its run,
     * a run that has not ended, or {@code debateRounds} below 1.
     */
    void validate(List<ExternalAnalysis> externals);
}
