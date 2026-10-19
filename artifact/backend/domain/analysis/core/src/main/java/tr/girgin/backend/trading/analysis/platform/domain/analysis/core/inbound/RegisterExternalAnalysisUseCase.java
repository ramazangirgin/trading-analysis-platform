package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;

/** Records a run found in the data dir (made outside the platform) as an EXTERNAL analysis. */
public interface RegisterExternalAnalysisUseCase {

    /**
     * Idempotent per data dir source; runs the platform started are never overwritten. A new record belongs to
     * {@code owner} (may be null); a refreshed one keeps its stored owner.
     */
    ExternalRegistration register(ExternalAnalysis external, OwnerId owner);
}
