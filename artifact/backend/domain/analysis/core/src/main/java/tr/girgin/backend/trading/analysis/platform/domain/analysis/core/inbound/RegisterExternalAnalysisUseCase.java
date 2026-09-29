package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration;

public interface RegisterExternalAnalysisUseCase {

    /** Idempotent per ticker and trade date; runs the platform started are never overwritten. */
    ExternalRegistration register(ExternalAnalysis external);
}
